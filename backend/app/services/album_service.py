from typing import List, Optional
from ..repositories.album_repo import album_repo
from ..schemas.album import AlbumCreate, FrameResponse, parse_timestamp
from .local_storage_service import save_video, save_photo
from ..utils.embeddings import embedding_gen
import os
from datetime import datetime

class AlbumService:
    async def create_album_multipart(self, db, album_in_name: str, album_in_desc: Optional[str], access_code: str, video_file, photos: List, frame_mappings: Optional[List[dict]] = None):
        # 1. Save video locally (returns a public URL)
        video_url = await save_video(video_file)

        # 2. Build a lookup for frame timestamps (photoId → {startTime, endTime})
        ts_lookup = {}
        if frame_mappings:
            for m in frame_mappings:
                ts_lookup[m["photoId"]] = {
                    "start_time": parse_timestamp(m.get("startTime", "0:00")),
                    "end_time": parse_timestamp(m.get("endTime", "0:10")),
                }

        # 3. Process photos
        processed_frames = []

        for photo in photos:
            # Save photo locally (returns the filesystem path)
            photo_path = await save_photo(photo)

            # Generate embedding from the saved file
            image_signature = []
            if embedding_gen:
                try:
                    image_signature = embedding_gen.generate_embedding(photo_path)
                except Exception as e:
                    print(f"Embedding generation failed for {photo.filename}: {e}")

            # Use provided timestamps or default to 0.0 / 10.0
            ts = ts_lookup.get(photo.filename, {"start_time": 0.0, "end_time": 10.0})

            processed_frames.append({
                "photo_id": photo.filename,
                "start_time": ts["start_time"],
                "end_time": ts["end_time"],
                "photo_url": photo_path,
                "image_signature": image_signature
            })

        # 3. Save to DB
        album_dict = {
            "name": album_in_name,
            "description": album_in_desc,
            "access_code": access_code,
            "video_url": video_url,
            "frames": processed_frames,
            "created_at": datetime.utcnow()
        }

        return await album_repo.create(db, album_dict)

    async def create_album_json(self, db, album_in: AlbumCreate):
        # Mapping to Document Structure
        album_dict = {
            "name": album_in.name or "Untitled Album",
            "description": album_in.description,
            "access_code": album_in.accessCode,
            "video_url": album_in.videoUrl,
            "frames": [
                {
                    "photo_id": f.photoId,
                    "start_time": f.startTime,
                    "end_time": f.endTime,
                    "photo_url": f.photoUrl,
                    "image_signature": f.imageSignature
                } for f in album_in.frames
            ],
            "created_at": datetime.utcnow()
        }
        
        return await album_repo.create(db, album_dict)

    async def get_album_for_mobile(self, db, album_id: str):
        album = await album_repo.get_by_id(db, album_id)
        if not album:
            return None
            
        return {
            "albumId": str(album["_id"]),
            "accessCode": album["access_code"],
            "videoUrl": album["video_url"],
            "frames": [
                {
                    "photoId": f["photo_id"],
                    "startTime": f["start_time"],
                    "endTime": f["end_time"],
                    "photoUrl": f["photo_url"],
                    "imageSignature": f["image_signature"]
                } for f in album["frames"]
            ]
        }

    async def unlock_by_access_code(self, db, access_code: str, request=None):
        album = await album_repo.get_by_access_code(db, access_code)
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
            for local in ["http://127.0.0.1:8000", "http://0.0.0.0:8000", "http://localhost:8000"]:
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
                sig = base64.b64encode(struct.pack(f"<{len(sig)}f", *sig)).decode("utf-8")

            frames.append({
                "id": f.get("photo_id", f"frame-{i:03d}"),
                "album_id": album_id,
                "index": i,
                "image_signature": sig,
                "video_url": video_url,
                "thumbnail_url": _rewrite_url(f.get("photo_url", "")),
                "duration_ms": duration_ms,
                "start_time_ms": start_ms,
                "metadata": {}
            })

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
                "total_frames": len(frames)
            },
            "message": "Success"
        }

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
            "totalFrames": len(updated_frames)
        }

album_service = AlbumService()
