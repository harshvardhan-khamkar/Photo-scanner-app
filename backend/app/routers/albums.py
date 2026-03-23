from fastapi import APIRouter, Depends, HTTPException, Request
from ..schemas.album import (
    AlbumCreate,
    AlbumResponse,
    AlbumUnlockResponse,
    MobileUnlockResponseV2,
    ForgotCodeRequest,
    ResetCodeRequest,
    ChangeCodeRequest,
)
from ..services.album_service import album_service
from ..database import get_database
from ..utils.security import get_admin_api_key
from ..utils.rate_limiter import limiter

router = APIRouter(prefix="/albums", tags=["Albums"])


@router.post("/", response_model=dict, status_code=201)
async def create_album(
    album_in: AlbumCreate,
    db=Depends(get_database),
    admin_key: str = Depends(get_admin_api_key),
):
    """
    Create a new wedding album with video and photo frames.
    Requires admin API key.
    """
    try:
        album_id = await album_service.create_album_json(db, album_in)
        return {"albumId": album_id}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/unlock/{access_code}", response_model=MobileUnlockResponseV2)
@limiter.limit("10/minute")
async def unlock_album_by_code(
    request: Request,
    access_code: str,
    db=Depends(get_database),
):
    """
    Unlock an album by its access code for mobile recognition.

    The access code is verified against bcrypt hashes stored in the database.
    Rate limited to 10 requests per minute per IP to prevent brute-force.
    This endpoint is public — no admin key required.
    """
    album = await album_service.unlock_by_access_code(db, access_code, request=request)
    if not album:
        raise HTTPException(
            status_code=404, detail="Album not found with this access code"
        )
    return album


# ── Forgot / Reset / Change access code ──────────────────────────────


@router.post("/forgot-code")
@limiter.limit("5/minute")
async def forgot_code(
    request: Request,
    body: ForgotCodeRequest,
    db=Depends(get_database),
):
    """
    Request a reset link for the album access code.

    Always returns a generic success message regardless of whether the email
    exists, to prevent email enumeration attacks (OWASP best practice).

    NOTE: In production, integrate a real email service (SendGrid, SES, etc.)
    instead of printing the reset URL to the console.

    Rate limited to 5 requests per minute per IP.
    """
    return await album_service.forgot_code(db, body.email, request=request)


@router.post("/reset-code")
@limiter.limit("5/minute")
async def reset_code(
    request: Request,
    body: ResetCodeRequest,
    db=Depends(get_database),
):
    """
    Reset album access code using a one-time token.

    The token must be valid and not expired (15 minute window).
    The new access code is hashed with bcrypt before storage.

    Rate limited to 5 requests per minute per IP.
    """
    return await album_service.reset_code(db, body.token, body.newAccessCode)


@router.post("/change-code")
@limiter.limit("5/minute")
async def change_code(
    request: Request,
    body: ChangeCodeRequest,
    db=Depends(get_database),
):
    """
    Change album access code by verifying the old code.

    Requires the current (old) access code for authentication.
    The new access code is hashed with bcrypt before storage.

    Rate limited to 5 requests per minute per IP.
    """
    return await album_service.change_code(
        db, body.albumId, body.oldAccessCode, body.newAccessCode
    )
