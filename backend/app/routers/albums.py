from fastapi import APIRouter, Depends, HTTPException, Request
from ..schemas.album import AlbumCreate, AlbumResponse, AlbumUnlockResponse, MobileUnlockResponseV2
from ..services.album_service import album_service
from ..database import get_database

router = APIRouter(prefix="/albums", tags=["Albums"])

@router.post("/", response_model=dict, status_code=201)
async def create_album(
    album_in: AlbumCreate,
    db = Depends(get_database)
):
    """
    Create a new wedding album with video and photo frames.
    """
    try:
        album_id = await album_service.create_album_json(db, album_in)
        return {"albumId": album_id}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@router.get("/unlock/{access_code}", response_model=MobileUnlockResponseV2)
async def unlock_album_by_code(
    access_code: str,
    request: Request,
    db = Depends(get_database)
):
    """
    Unlock an album by its access code for mobile recognition.
    """
    album = await album_service.unlock_by_access_code(db, access_code, request=request)
    if not album:
        raise HTTPException(status_code=404, detail="Album not found with this access code")
    return album
