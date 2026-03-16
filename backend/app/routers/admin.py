from fastapi import APIRouter, Depends, UploadFile, File, Form, HTTPException
from typing import List, Optional
from ..schemas.album import AlbumCreate, AlbumResponse, FrameMappingRequest
from ..services.album_service import album_service
from ..database import get_database
from ..utils.security import get_admin_api_key
import json

router = APIRouter(prefix="/admin", tags=["Admin"])


@router.get("/albums")
async def list_albums(
    db=Depends(get_database),
    admin_key: str = Depends(get_admin_api_key),
):
    """List all albums with basic info for the admin dashboard."""
    from ..repositories.album_repo import album_repo
    albums = await album_repo.get_all(db)
    result = []
    for a in albums:
        result.append({
            "id": str(a["_id"]),
            "name": a.get("name", "Untitled"),
            "access_code": a.get("access_code", ""),
            "video_url": a.get("video_url", ""),
            "total_frames": len(a.get("frames", [])),
            "created_at": str(a.get("created_at", "")),
            "frames": [
                {
                    "photo_id": f.get("photo_id", ""),
                    "start_time": f.get("start_time", 0),
                    "end_time": f.get("end_time", 0),
                    "photo_url": f.get("photo_url", ""),
                }
                for f in a.get("frames", [])
            ],
        })
    return result


@router.delete("/albums/{album_id}")
async def delete_album(
    album_id: str,
    db=Depends(get_database),
    admin_key: str = Depends(get_admin_api_key),
):
    """Delete an album by ID."""
    from ..repositories.album_repo import album_repo
    album = await album_repo.get_by_id(db, album_id)
    if not album:
        raise HTTPException(status_code=404, detail="Album not found")

    from bson import ObjectId
    await db["albums"].delete_one({"_id": ObjectId(album_id)})
    return {"message": "Album deleted", "album_id": album_id}


@router.post("/upload-album")
async def upload_album(
    name: str = Form(...),
    description: Optional[str] = Form(None),
    access_code: str = Form(...),
    video: UploadFile = File(...),
    photos: List[UploadFile] = File(...),
    frame_mappings: Optional[str] = Form(None),
    db=Depends(get_database),
    admin_key: str = Depends(get_admin_api_key),
):
    # Parse optional frame_mappings JSON: [{"photoId":"...", "startTime":0.0, "endTime":15.5}, ...]
    parsed_mappings = None
    if frame_mappings:
        try:
            parsed_mappings = json.loads(frame_mappings)
        except json.JSONDecodeError:
            raise HTTPException(status_code=400, detail="frame_mappings must be valid JSON")

    album_id = await album_service.create_album_multipart(
        db, name, description, access_code, video, photos,
        frame_mappings=parsed_mappings,
    )
    return {"album_id": album_id, "message": "Album uploaded successfully"}


@router.put("/albums/{album_id}/map-frames")
async def map_frames(
    album_id: str,
    mapping: FrameMappingRequest,
    db=Depends(get_database),
    admin_key: str = Depends(get_admin_api_key),
):
    """Map photo frames to video timestamps for an existing album."""
    result = await album_service.map_frames(db, album_id, mapping.frames)
    if not result:
        raise HTTPException(status_code=404, detail="Album not found")
    return result
