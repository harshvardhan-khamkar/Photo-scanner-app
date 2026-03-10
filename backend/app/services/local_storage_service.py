"""
Local Storage Service
---------------
Drop-in replacement for S3 uploads during local development.
Files are persisted under the project-level ``storage/`` directory
and served via FastAPI's StaticFiles mount.

To switch back to S3, simply swap the imports in album_service.py.
"""

import os
import shutil
import subprocess
import uuid
import logging
from fastapi import UploadFile

logger = logging.getLogger(__name__)

# ── paths relative to the CWD (project root) ──────────────────────────
STORAGE_ROOT = os.path.join("storage")
VIDEOS_DIR = os.path.join(STORAGE_ROOT, "videos")
PHOTOS_DIR = os.path.join(STORAGE_ROOT, "photos")

# Base URL used to construct public video URLs (fallback)
BASE_URL = "http://0.0.0.0:8000"


def get_base_url(request=None) -> str:
    """Build base URL from the incoming request's Host header when available."""
    if request:
        host = request.headers.get("host", "0.0.0.0:8000")
        return f"http://{host}"
    return BASE_URL

# Ensure directories exist on module load
os.makedirs(VIDEOS_DIR, exist_ok=True)
os.makedirs(PHOTOS_DIR, exist_ok=True)


def _unique_filename(original_filename: str) -> str:
    """Generate a UUID-based filename preserving the original extension."""
    _, ext = os.path.splitext(original_filename or "")
    if not ext:
        ext = ""
    return f"{uuid.uuid4().hex}{ext}"


def _optimize_video(path: str) -> None:
    """
    Re-mux an MP4 so ExoPlayer can seek over HTTP.

    * Converts fragmented MP4 (moof/mdat) → regular MP4 (single moov + mdat).
    * Moves the moov atom to the front (``-movflags +faststart``).
    * Uses ``-c copy`` so there is no re-encoding — only container changes.
    * If ffmpeg is not installed, the original file is kept as-is and a
      warning is logged.
    """
    if not shutil.which("ffmpeg"):
        logger.warning("ffmpeg not found — skipping video optimization for %s", path)
        return

    tmp_path = path + ".optimizing.mp4"
    try:
        result = subprocess.run(
            [
                "ffmpeg", "-y",
                "-i", path,
                "-c", "copy",
                "-movflags", "+faststart",
                tmp_path,
            ],
            capture_output=True,
            text=True,
            timeout=600,  # 10-minute timeout for large files
        )
        if result.returncode == 0 and os.path.exists(tmp_path):
            os.replace(tmp_path, path)  # atomic replace
            logger.info("Video optimized (faststart) → %s", path)
        else:
            logger.warning("ffmpeg failed (rc=%d): %s", result.returncode, result.stderr[-500:] if result.stderr else "")
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
    except subprocess.TimeoutExpired:
        logger.warning("ffmpeg timed out for %s", path)
        if os.path.exists(tmp_path):
            os.remove(tmp_path)
    except Exception as e:
        logger.warning("Video optimization failed: %s", e)
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


async def save_video(file: UploadFile) -> str:
    """
    Save an uploaded video to ``storage/videos/``.

    The video is automatically post-processed with ffmpeg to ensure it is
    a non-fragmented MP4 with the moov atom at the front (faststart),
    which is required for ExoPlayer to seek over progressive HTTP.

    Returns
    -------
    str
        A public URL of the form
        ``http://127.0.0.1:8000/storage/videos/<uuid>.<ext>``
    """
    filename = _unique_filename(file.filename)
    dest_path = os.path.join(VIDEOS_DIR, filename)

    contents = await file.read()
    with open(dest_path, "wb") as f:
        f.write(contents)

    # Defragment + faststart for seekable HTTP playback
    _optimize_video(dest_path)

    url = f"{BASE_URL}/storage/videos/{filename}"
    logger.info("Video saved → %s", url)
    return url


async def save_photo(file: UploadFile) -> str:
    """
    Save an uploaded photo to ``storage/photos/``.

    Returns
    -------
    str
        The **local filesystem path** to the saved file.
        This path is consumed directly by the embedding generator.
    """
    filename = _unique_filename(file.filename)
    dest_path = os.path.join(PHOTOS_DIR, filename)

    contents = await file.read()
    with open(dest_path, "wb") as f:
        f.write(contents)

    logger.info("Photo saved → %s", dest_path)
    return dest_path
