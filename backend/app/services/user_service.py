import logging
from datetime import datetime
from fastapi import HTTPException, status
from ..repositories.user_repo import user_repo

logger = logging.getLogger(__name__)


class UserService:
    async def create_user(self, db, email: str, password: str) -> dict:
        """
        Create a new user with a hashed password.
        Raises 409 if the email already exists.
        """
        existing = await user_repo.get_by_email(db, email)
        if existing:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="A user with this email already exists",
            )

        user_data = {
            "email": email,
            "created_at": datetime.utcnow(),
        }
        user_id = await user_repo.create(db, user_data)
        logger.info("Created user %s (%s)", user_id, email)

        return {
            "userId": user_id,
            "email": email,
            "createdAt": user_data["created_at"],
        }

    async def get_user(self, db, user_id: str) -> dict:
        """Return user info (excluding password). Raises 404 if not found."""
        user = await user_repo.get_by_id(db, user_id)
        if not user:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="User not found",
            )
        return {
            "userId": str(user["_id"]),
            "email": user["email"],
            "createdAt": user["created_at"],
        }


user_service = UserService()
