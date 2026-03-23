import logging
from typing import List, Optional
from datetime import datetime, timedelta
from fastapi import HTTPException, status
from ..repositories.album_repo import album_repo
from ..repositories.user_repo import user_repo
from ..schemas.album import AlbumCreate, FrameResponse, parse_timestamp
from .local_storage_service import save_video, save_photo
from .email_service import email_service
from ..utils.embeddings import embedding_gen
from ..utils.hashing import hash_password, verify_password, generate_reset_token, hash_token
import os

logger = logging.getLogger(__name__)

# Reset token validity window
RESET_TOKEN_EXPIRY_MINUTES = 15


class AlbumService:
    # ── Album creation (multipart upload from admin UI) ───────────────
    async def create_album_multipart(
        self,
        db,
        album_in_name: str,
        album_in_desc: Optional[str],
        access_code: str,
        video_file,
        photos: List,
        frame_mappings: List[dict],
        owner_email: Optional[str] = None,
    ):
        # 1. Resolve owner (find or create user stub)
        owner_id = None
        if owner_email:
            owner_id = await user_repo.find_or_create(db, owner_email)
            logger.info("Resolved owner %s → %s", owner_email, owner_id)

        # 2. Save video locally (returns a public URL)
        video_url = await save_video(video_file)

        # 3. Strict Validation and Timestamp Parsing
        if not frame_mappings or len(frame_mappings) != len(photos):
            raise HTTPException(status_code=400, detail="Missing or mismatched frame mappings count")

        mapping_dict = {}
        for m in frame_mappings:
            idx = m.get("photoIndex")
            if idx is None or not isinstance(idx, int) or idx < 0 or idx >= len(photos):
                raise HTTPException(status_code=400, detail=f"Invalid photoIndex: {idx}")
            
            start_sec = parse_timestamp(m.get("startTime", "0:00"))
            end_sec = parse_timestamp(m.get("endTime", "0:10"))
            
            if start_sec >= end_sec:
                raise HTTPException(status_code=400, detail=f"startTime must be before endTime for index {idx}")
                
            mapping_dict[idx] = {
                "start_time": start_sec,
                "end_time": end_sec,
            }

        if len(mapping_dict) != len(photos):
            raise HTTPException(status_code=400, detail="Duplicate or missing photoIndex in mappings")

        # 4. Process photos
        processed_frames = []

        for i, photo in enumerate(photos):
            # Save photo locally (returns the filesystem path)
            photo_path = await save_photo(photo)

            # Generate embedding from the saved file
            image_signature = []
            if embedding_gen:
                try:
                    image_signature = embedding_gen.generate_embedding(photo_path)
                except Exception as e:
                    logger.error("Embedding generation failed for %s: %s", photo.filename, e)

            ts = mapping_dict[i]
            
            processed_frames.append({
                "photo_id": photo.filename,
                "start_time": ts["start_time"],
                "end_time": ts["end_time"],
                "photo_url": photo_path,
                "image_signature": image_signature
            })

        # Opt Improvement: Sort explicitly by start_time so app plays smoothly chronologically
        processed_frames.sort(key=lambda x: x["start_time"])

        # 5. Hash the access code before storing
        hashed_code = hash_password(access_code)

        # 6. Save to DB
        album_dict = {
            "name": album_in_name,
            "description": album_in_desc,
            "access_code": hashed_code,
            "video_url": video_url,
            "frames": processed_frames,
            "owner_id": owner_id,
            "created_at": datetime.utcnow(),
        }

        album_id = await album_repo.create(db, album_dict)
        logger.info("Created album %s (owner=%s)", album_id, owner_id)
        return album_id

    # ── Album creation (JSON payload) ─────────────────────────────────
    async def create_album_json(self, db, album_in: AlbumCreate):
        hashed_code = hash_password(album_in.accessCode)

        album_dict = {
            "name": album_in.name or "Untitled Album",
            "description": album_in.description,
            "access_code": hashed_code,
            "video_url": album_in.videoUrl,
            "frames": [
                {
                    "photo_id": f.photoId,
                    "start_time": f.startTime,
                    "end_time": f.endTime,
                    "photo_url": f.photoUrl,
                    "image_signature": f.imageSignature,
                }
                for f in album_in.frames
            ],
            "created_at": datetime.utcnow(),
        }

        return await album_repo.create(db, album_dict)

    # ── Mobile album fetch (by album ID) ──────────────────────────────
    async def get_album_for_mobile(self, db, album_id: str):
        album = await album_repo.get_by_id(db, album_id)
        if not album:
            return None

        return {
            "albumId": str(album["_id"]),
            "accessCode": "[REDACTED]",  # Never expose the hash
            "videoUrl": album["video_url"],
            "frames": [
                {
                    "photoId": f["photo_id"],
                    "startTime": f["start_time"],
                    "endTime": f["end_time"],
                    "photoUrl": f["photo_url"],
                    "imageSignature": f["image_signature"],
                }
                for f in album["frames"]
            ],
        }

    # ── Unlock by access code (bcrypt scan) ───────────────────────────
    async def unlock_by_access_code(self, db, access_code: str, request=None):
        """
        Unlock an album by verifying the plain access code against all
        stored bcrypt hashes.

        Performance Note:
        -----------------
        This iterates over ALL albums and runs bcrypt.verify on each.
        For < 1000 albums this is perfectly fine (~1-2s worst case).
        For larger scale, store a non-secret prefix (first 4 chars) as
        'access_code_hint' and query by that first to reduce the scan.
        """
        all_albums = await album_repo.get_all_albums(db)

        matched_album_id = None
        for album_stub in all_albums:
            stored_hash = album_stub.get("access_code", "")
            # Skip albums with unhashed (legacy) or empty codes gracefully
            if not stored_hash.startswith("$2b$"):
                # Legacy plain-text code: direct comparison
                if stored_hash == access_code:
                    matched_album_id = str(album_stub["_id"])
                    break
                continue
            if verify_password(access_code, stored_hash):
                matched_album_id = str(album_stub["_id"])
                break

        if not matched_album_id:
            return None

        # Fetch the full album document
        album = await album_repo.get_by_id(db, matched_album_id)
        if not album:
            return None

        # Dynamically rewrite stored 127.0.0.1 URLs to use the request's host
        base_url = ""
        if request:
            host = request.headers.get("host", "")
            if host:
                base_url = f"http://{host}"

        album_id = str(album["_id"])
        video_url = album.get("video_url", "")
        created_at = album.get("created_at", datetime.utcnow())

        # Rewrite stored local URLs to use the client's request host
        def _rewrite_url(url: str) -> str:
            if not base_url or not url:
                return url
            for local in [
                "http://127.0.0.1:8000",
                "http://0.0.0.0:8000",
                "http://localhost:8000",
            ]:
                if local in url:
                    return url.replace(local, base_url)
            return url

        video_url = _rewrite_url(video_url)

        # Convert created_at to epoch millis
        if isinstance(created_at, datetime):
            created_at_ms = int(created_at.timestamp() * 1000)
        else:
            created_at_ms = int(created_at)

        frames = []
        for i, f in enumerate(album.get("frames", [])):
            start = f.get("start_time", 0.0)
            end = f.get("end_time", 0.0)
            start_ms = int(start * 1000)
            end_ms = int(end * 1000)
            duration_ms = end_ms - start_ms

            # image_signature: pass through as-is (Base64 string from embedding generator)
            sig = f.get("image_signature", "")
            if isinstance(sig, list):
                # Legacy: if stored as a raw float list, encode to Base64
                import struct, base64

                sig = base64.b64encode(struct.pack(f"<{len(sig)}f", *sig)).decode(
                    "utf-8"
                )

            frames.append(
                {
                    "id": f.get("photo_id", f"frame-{i:03d}"),
                    "album_id": album_id,
                    "index": i,
                    "image_signature": sig,
                    "video_url": video_url,
                    "thumbnail_url": _rewrite_url(f.get("photo_url", "")),
                    "duration_ms": duration_ms,
                    "start_time_ms": start_ms,
                    "metadata": {},
                }
            )

        return {
            "album": {
                "id": album_id,
                "code": access_code,
                "name": album.get("name", "Wedding Album"),
                "cover_image_url": frames[0]["thumbnail_url"] if frames else "",
                "frames": frames,
                "status": "ready",
                "created_at": created_at_ms,
                "expires_at": None,
                "total_frames": len(frames),
            },
            "message": "Success",
        }

    # ── Map frames ────────────────────────────────────────────────────
    async def map_frames(self, db, album_id: str, frame_mappings: list):
        """Update frame timestamps for an existing album."""
        album = await album_repo.get_by_id(db, album_id)
        if not album:
            return None

        # Build a lookup from the incoming mappings
        mapping_lookup = {m.photoId: m for m in frame_mappings}

        # Update timestamps on matching frames
        updated_frames = []
        for frame in album["frames"]:
            mapping = mapping_lookup.get(frame["photo_id"])
            if mapping:
                frame["start_time"] = mapping.start_seconds
                frame["end_time"] = mapping.end_seconds
            updated_frames.append(frame)

        await album_repo.update(db, album_id, {"frames": updated_frames})
        return {
            "albumId": album_id,
            "updatedFrames": len(mapping_lookup),
            "totalFrames": len(updated_frames),
        }

    # ── Forgot access code ────────────────────────────────────────────
    async def forgot_code(self, db, email: str, request=None) -> dict:
        """
        Generate a reset token for all albums owned by this user.

        SECURITY: Always returns a generic success message regardless of
        whether the email exists. This prevents email enumeration attacks.
        See: https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html
        """
        generic_response = {
            "message": "If an account with that email exists, a reset link has been sent."
        }

        # 1. Find user by email
        user = await user_repo.get_by_email(db, email)
        if not user:
            logger.info("Forgot-code requested for unknown email (not revealing)")
            return generic_response

        user_id = str(user["_id"])

        # 2. Get all albums of this user
        albums = await album_repo.get_by_owner_id(db, user_id)
        if not albums:
            logger.info("User %s has no albums", user_id)
            return generic_response

        # 3. Generate reset token
        raw_token = generate_reset_token()
        hashed = hash_token(raw_token)
        expiry = datetime.utcnow() + timedelta(minutes=RESET_TOKEN_EXPIRY_MINUTES)

        # 4. Store hashed token + expiry on ALL user's albums
        await album_repo.update_many_by_owner(
            db,
            user_id,
            {"reset_token": hashed, "reset_token_expiry": expiry},
        )

        # 5. Send the reset email
        
        # Determine the base URL dynamically from the request headers
        base_url = "http://localhost:8000"
        if request:
            host = request.headers.get("host")
            if host:
                scheme = request.headers.get("x-forwarded-proto", request.url.scheme)
                base_url = f"{scheme}://{host}"
                
        reset_url = f"{base_url}/reset-code?token={raw_token}"
        await email_service.send_reset_email(email, reset_url)

        return generic_response

    # ── Reset access code ─────────────────────────────────────────────
    async def reset_code(self, db, token: str, new_access_code: str) -> dict:
        """
        Reset an album's access code using a one-time token.

        Flow:
        1. Hash the token → look up in DB
        2. Validate expiry
        3. Hash new access code with bcrypt
        4. Update album
        5. Clear reset token fields

        NOTE: Rate limiting should be applied at the router level.
        """
        # 1. Hash the provided token for DB lookup
        hashed = hash_token(token)

        # 2. Find matching album
        album = await album_repo.get_by_reset_token(db, hashed)
        if not album:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid or expired reset token",
            )

        # 3. Check expiry
        expiry = album.get("reset_token_expiry")
        if not expiry or datetime.utcnow() > expiry:
            # Clean up expired token
            await album_repo.clear_reset_token(db, str(album["_id"]))
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid or expired reset token",
            )

        album_id = str(album["_id"])

        # 4. Hash new access code
        new_hashed_code = hash_password(new_access_code)

        # 5. Update album with new hashed code
        await album_repo.update(db, album_id, {"access_code": new_hashed_code})

        # 6. Clear reset token fields
        await album_repo.clear_reset_token(db, album_id)

        logger.info("Access code reset successfully for album %s", album_id)
        return {"message": "Access code has been reset successfully"}

    # ── Change access code (authenticated by old code) ────────────────
    async def change_code(
        self, db, album_id: str, old_access_code: str, new_access_code: str
    ) -> dict:
        """
        Change an album's access code by verifying the old code first.

        NOTE: Rate limiting should be applied at the router level.
        """
        album = await album_repo.get_by_id(db, album_id)
        if not album:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND, detail="Album not found"
            )

        stored_hash = album.get("access_code", "")

        # Support both hashed and legacy plain-text codes
        if stored_hash.startswith("$2b$"):
            if not verify_password(old_access_code, stored_hash):
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Old access code is incorrect",
                )
        else:
            # Legacy plain-text comparison
            if stored_hash != old_access_code:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Old access code is incorrect",
                )

        # Hash and update
        new_hashed_code = hash_password(new_access_code)
        await album_repo.update(db, album_id, {"access_code": new_hashed_code})

        logger.info("Access code changed for album %s", album_id)
        return {"message": "Access code changed successfully"}


album_service = AlbumService()
