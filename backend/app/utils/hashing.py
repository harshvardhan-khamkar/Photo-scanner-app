"""
Hashing and token utilities for the Wedding Memory Platform.

Uses passlib's bcrypt for password/access-code hashing and
Python's secrets + hashlib for reset token generation.
"""

import hashlib
import secrets
from passlib.context import CryptContext

# ── Bcrypt context ────────────────────────────────────────────────────
# "auto" means passlib will transparently upgrade deprecated schemes
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(plain: str) -> str:
    """Hash a password or access code using bcrypt."""
    return pwd_context.hash(plain)


def verify_password(plain: str, hashed: str) -> bool:
    """Verify a plain password/code against a bcrypt hash."""
    return pwd_context.verify(plain, hashed)


# ── Reset token helpers ──────────────────────────────────────────────
def generate_reset_token() -> str:
    """Generate a cryptographically secure URL-safe reset token."""
    return secrets.token_urlsafe(32)


def hash_token(token: str) -> str:
    """
    Deterministic SHA-256 hash of a reset token.

    We store the SHA-256 digest in DB so we can look up by it,
    while the raw token is only ever sent to the user.
    """
    return hashlib.sha256(token.encode("utf-8")).hexdigest()
