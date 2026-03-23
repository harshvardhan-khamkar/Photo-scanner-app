from fastapi import APIRouter, Depends
from ..schemas.user import UserCreate, UserResponse
from ..services.user_service import user_service
from ..database import get_database

router = APIRouter(prefix="/users", tags=["Users"])


@router.post("/create", response_model=UserResponse, status_code=201)
async def create_user(body: UserCreate, db=Depends(get_database)):
    """
    Register a new user with email and password.

    The password is hashed with bcrypt before storage.
    Returns user info (password is never exposed).
    """
    return await user_service.create_user(db, body.email, body.password)


@router.get("/{user_id}", response_model=UserResponse)
async def get_user(user_id: str, db=Depends(get_database)):
    """
    Get user information by ID.

    Password is excluded from the response.
    """
    return await user_service.get_user(db, user_id)
