from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime
from bson import ObjectId

class PyObjectId(ObjectId):
    @classmethod
    def __get_validators__(cls):
        yield cls.validate

    @classmethod
    def validate(cls, v):
        if not ObjectId.is_valid(v):
            raise ValueError("Invalid objectid")
        return ObjectId(v)

    @classmethod
    def __get_pydantic_json_schema__(cls, field_schema):
        field_schema.update(type="string")

class FrameDocument(BaseModel):
    photo_id: str
    start_time: float
    end_time: float
    photo_url: str
    image_signature: List[float]

class AlbumDocument(BaseModel):
    id: Optional[PyObjectId] = Field(alias="_id", default=None)
    name: str
    description: Optional[str] = None
    access_code: str  # Stored as bcrypt hash
    video_url: str
    frames: List[FrameDocument] = []
    owner_id: Optional[str] = None  # Reference to User._id
    reset_token: Optional[str] = None  # SHA-256 hash of reset token
    reset_token_expiry: Optional[datetime] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Config:
        populate_by_name = True
        json_encoders = {ObjectId: str}
