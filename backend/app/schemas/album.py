from pydantic import BaseModel, EmailStr, Field
from typing import List, Optional
from datetime import datetime

class FrameBase(BaseModel):
    photoId: str
    startTime: float
    endTime: float
    photoUrl: str
    imageSignature: str

class FrameCreate(FrameBase):
    pass

class FrameResponse(FrameBase):
    pass

class AlbumBase(BaseModel):
    name: str = Field(..., example="Sanika & Rahul's Wedding")
    description: Optional[str] = None
    accessCode: str = Field(..., example="WED2026")

class AlbumCreate(BaseModel):
    accessCode: str = Field(..., example="WED2026")
    videoUrl: str = Field(..., example="https://s3.amazonaws.com/.../video.mp4")
    frames: List[FrameCreate]
    name: Optional[str] = None
    description: Optional[str] = None

class AlbumResponse(AlbumBase):
    albumId: str
    accessCode: str
    videoUrl: str
    frames: List[FrameResponse]
    createdAt: datetime

class MobileFrameResponse(BaseModel):
    photoId: str
    startTime: float
    endTime: float
    imageSignature: str

class AlbumUnlockResponse(BaseModel):
    albumId: str
    videoUrl: str
    frames: List[MobileFrameResponse]

def parse_timestamp(val) -> float:
    """Parse a timestamp that can be either:
       - A string like "1:20" or "03:35" (M:SS or MM:SS) → converts to seconds
       - A raw number in seconds (e.g. 80.0) → returned as-is
    """
    if isinstance(val, str) and ":" in val:
        parts = val.strip().split(":")
        if len(parts) == 2:
            minutes = int(parts[0])
            seconds = int(parts[1])
            return float(minutes * 60 + seconds)
        elif len(parts) == 3:  # H:MM:SS
            hours = int(parts[0])
            minutes = int(parts[1])
            seconds = int(parts[2])
            return float(hours * 3600 + minutes * 60 + seconds)
    return float(val)

class FrameMappingItem(BaseModel):
    photoId: str = Field(..., example="photo1.jpg")
    startTime: str = Field(..., example="1:20", description="Start time as M:SS (e.g. '1:20') or seconds (e.g. '80')")
    endTime: str = Field(..., example="3:35", description="End time as M:SS (e.g. '3:35') or seconds (e.g. '215')")

    @property
    def start_seconds(self) -> float:
        return parse_timestamp(self.startTime)

    @property
    def end_seconds(self) -> float:
        return parse_timestamp(self.endTime)

class FrameMappingRequest(BaseModel):
    frames: List[FrameMappingItem]

# ── Mobile V2 response models (matches Android DTOs) ──────────────────

class MobileFrameV2(BaseModel):
    id: str
    album_id: str
    index: int
    image_signature: str
    video_url: str
    thumbnail_url: str
    duration_ms: int
    start_time_ms: int = 0
    metadata: dict = {}

class MobileAlbumV2(BaseModel):
    id: str
    code: str
    name: str
    cover_image_url: str
    frames: List[MobileFrameV2]
    status: str
    created_at: int
    expires_at: Optional[int] = None
    total_frames: int

class MobileUnlockResponseV2(BaseModel):
    album: MobileAlbumV2
    message: str = "Success"


# ── Access code management schemas ────────────────────────────────────

class ForgotCodeRequest(BaseModel):
    email: str = Field(..., example="user@example.com")


class ResetCodeRequest(BaseModel):
    token: str = Field(..., example="abc123...")
    newAccessCode: str = Field(..., min_length=4, example="1234")


class ChangeCodeRequest(BaseModel):
    albumId: str = Field(..., example="60d...")
    oldAccessCode: str = Field(..., example="WED2026")
    newAccessCode: str = Field(..., min_length=4, example="1234")


class AdminUpdateOwnerEmail(BaseModel):
    email: EmailStr = Field(..., example="owner@example.com")
