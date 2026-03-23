from pydantic import BaseModel, EmailStr
from datetime import datetime


class UserCreate(BaseModel):
    """Used only for the find-or-create stub when admin assigns an owner email to an album."""
    email: EmailStr


class UserResponse(BaseModel):
    userId: str
    email: str
    createdAt: datetime
