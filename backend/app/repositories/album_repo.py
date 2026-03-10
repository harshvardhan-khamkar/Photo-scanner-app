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
        return await db[self.collection].find_one({"access_code": access_code})

    async def create(self, db, album_data: dict) -> str:
        result = await db[self.collection].insert_one(album_data)
        return str(result.inserted_id)

    async def update(self, db, album_id: str, album_data: dict) -> bool:
        result = await db[self.collection].update_one(
            {"_id": ObjectId(album_id)},
            {"$set": album_data}
        )
        return result.modified_count > 0

album_repo = AlbumRepository()
