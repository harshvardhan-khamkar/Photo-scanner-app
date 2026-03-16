"""
Rate limiter configuration using slowapi.

Provides a singleton ``limiter`` and a shared key function
that extracts the client IP from each request.
"""

from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)
