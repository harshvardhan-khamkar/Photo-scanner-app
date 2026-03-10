from fastapi import APIRouter, Depends, UploadFile, File, Form, HTTPException
from typing import List, Optional
from ..schemas.album import AlbumCreate, AlbumResponse, FrameMappingRequest
from ..services.album_service import album_service
from ..database import get_database
from ..utils.security import get_admin_api_key
import json

router = APIRouter(prefix="/admin", tags=["Admin"])

@router.post("/upload-album")
async def upload_album(
    name: str = Form(...),
    description: Optional[str] = Form(None),
    access_code: str = Form(...),
    video: UploadFile = File(...),
    photos: List[UploadFile] = File(...),
    frame_mappings: Optional[str] = Form(None),
    db = Depends(get_database),
    admin_key: str = Depends(get_admin_api_key)
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
        frame_mappings=parsed_mappings
    )
    return {"album_id": album_id, "message": "Album uploaded successfully"}

@router.put("/albums/{album_id}/map-frames")
async def map_frames(
    album_id: str,
    mapping: FrameMappingRequest,
    db = Depends(get_database),
    admin_key: str = Depends(get_admin_api_key)
):
    """Map photo frames to video timestamps for an existing album."""
    result = await album_service.map_frames(db, album_id, mapping.frames)
    if not result:
        raise HTTPException(status_code=404, detail="Album not found")
    return result
