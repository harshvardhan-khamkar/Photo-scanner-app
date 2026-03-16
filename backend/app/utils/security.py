import secrets
import logging
from fastapi import Security, HTTPException, status
from fastapi.security.api_key import APIKeyHeader
from ..config import settings

logger = logging.getLogger(__name__)

API_KEY_NAME = "X-Admin-API-Key"
api_key_header = APIKeyHeader(name=API_KEY_NAME, auto_error=False)


async def get_admin_api_key(api_key: str = Security(api_key_header)):
    """
    Validate the admin API key from the request header.

    Uses ``secrets.compare_digest`` to prevent timing attacks — a constant-time
    comparison that doesn't leak information about which characters matched.
    """
    if api_key is None:
        logger.warning("Admin request with missing API key")
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Missing admin API key",
        )

    if not secrets.compare_digest(api_key, settings.ADMIN_API_KEY):
        logger.warning("Admin request with invalid API key")
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Could not validate admin credentials",
        )

    return api_key
