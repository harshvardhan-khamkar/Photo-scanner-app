from fastapi import APIRouter, Depends, HTTPException
from ..schemas.album import AlbumUnlockResponse
from ..services.album_service import album_service
from ..database import get_database

router = APIRouter(prefix="/mobile", tags=["Mobile"])

@router.get("/unlock/{album_id}", response_model=AlbumUnlockResponse)
async def unlock_album(album_id: str, db = Depends(get_database)):
    album = await album_service.get_album_for_mobile(db, album_id)
    if not album:
        raise HTTPException(status_code=404, detail="Album not found")
    return album
