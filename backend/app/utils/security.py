from fastapi import Security, HTTPException, status
from fastapi.security.api_key import APIKeyHeader
from ..config import settings

API_KEY_NAME = "X-Admin-API-Key"
api_key_header = APIKeyHeader(name=API_KEY_NAME, auto_error=False)

async def get_admin_api_key(api_key: str = Security(api_key_header)):
    if api_key == settings.SECRET_KEY:
        return api_key
    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail="Could not validate admin credentials"
    )
