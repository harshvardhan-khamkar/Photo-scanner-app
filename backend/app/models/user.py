from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from .album import PyObjectId


class UserDocument(BaseModel):
    id: Optional[PyObjectId] = Field(alias="_id", default=None)
    email: str
    # No password — this app uses album access codes, not user logins.
    # Users only exist for the "forgot code" email → album lookup.
    created_at: datetime = Field(default_factory=datetime.utcnow)

    class Config:
        populate_by_name = True
        json_encoders = {PyObjectId: str}
