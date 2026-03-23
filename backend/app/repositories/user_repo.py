from bson import ObjectId
from typing import Optional
from datetime import datetime


class UserRepository:
    def __init__(self):
        self.collection = "users"

    async def get_by_email(self, db, email: str) -> Optional[dict]:
        return await db[self.collection].find_one({"email": email})

    async def get_by_id(self, db, user_id: str) -> Optional[dict]:
        from bson.errors import InvalidId
        try:
            return await db[self.collection].find_one({"_id": ObjectId(user_id)})
        except InvalidId:
            return None

    async def create(self, db, user_data: dict) -> str:
        result = await db[self.collection].insert_one(user_data)
        return str(result.inserted_id)

    async def find_or_create(self, db, email: str) -> str:
        """
        Find an existing user by email, or create a stub user (no password).
        Returns the user's ID as a string.
        """
        existing = await self.get_by_email(db, email)
        if existing:
            return str(existing["_id"])

        user_data = {
            "email": email,
            # No password — this app uses album access codes, not user logins.
            # This record only exists to support "forgot code" email → album lookup.
            "created_at": datetime.utcnow(),
        }
        return await self.create(db, user_data)


user_repo = UserRepository()
