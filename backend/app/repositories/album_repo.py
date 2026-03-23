from bson import ObjectId
from typing import List, Optional
from ..models.album import AlbumDocument
from ..database import get_database


class AlbumRepository:
    def __init__(self):
        self.collection = "albums"

    async def get_all(self, db) -> List[dict]:
        return await db[self.collection].find().to_list(1000)

    async def get_by_id(self, db, album_id: str) -> Optional[dict]:
        return await db[self.collection].find_one({"_id": ObjectId(album_id)})

    async def get_by_access_code(self, db, access_code: str) -> Optional[dict]:
        """Legacy: direct lookup. Kept for reference but no longer used with hashed codes."""
        return await db[self.collection].find_one({"access_code": access_code})

    async def get_all_albums(self, db) -> List[dict]:
        """
        Fetch all albums (lightweight projection for bcrypt scan during unlock).

        NOTE (Performance): For large datasets, consider storing a non-secret
        prefix of the access code as 'access_code_hint' and querying by that
        first to reduce the scan scope. For <1000 albums this full scan is fine.
        """
        return await db[self.collection].find(
            {}, {"_id": 1, "access_code": 1}
        ).to_list(None)

    async def get_by_owner_id(self, db, owner_id: str) -> List[dict]:
        return await db[self.collection].find({"owner_id": owner_id}).to_list(None)

    async def get_by_reset_token(self, db, hashed_token: str) -> Optional[dict]:
        return await db[self.collection].find_one({"reset_token": hashed_token})

    async def create(self, db, album_data: dict) -> str:
        result = await db[self.collection].insert_one(album_data)
        return str(result.inserted_id)

    async def update(self, db, album_id: str, album_data: dict) -> bool:
        result = await db[self.collection].update_one(
            {"_id": ObjectId(album_id)},
            {"$set": album_data}
        )
        return result.modified_count > 0

    async def update_many_by_owner(self, db, owner_id: str, update_data: dict) -> int:
        """Update all albums belonging to an owner (used by forgot-code flow)."""
        result = await db[self.collection].update_many(
            {"owner_id": owner_id},
            {"$set": update_data}
        )
        return result.modified_count

    async def clear_reset_token(self, db, album_id: str) -> bool:
        """Remove reset token fields after successful reset."""
        result = await db[self.collection].update_one(
            {"_id": ObjectId(album_id)},
            {"$unset": {"reset_token": "", "reset_token_expiry": ""}}
        )
        return result.modified_count > 0


album_repo = AlbumRepository()
