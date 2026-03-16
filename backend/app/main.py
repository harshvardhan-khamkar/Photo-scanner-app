import os
import time
import logging
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.openapi.utils import get_openapi
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from .utils.rate_limiter import limiter
from .routers import admin, mobile, albums
from .database import connect_to_mongo, close_mongo_connection
from .config import settings
from contextlib import asynccontextmanager

logger = logging.getLogger(__name__)

# ── Configure root logging ────────────────────────────────────────────
logging.basicConfig(
    level=logging.DEBUG if settings.DEBUG else logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
# Suppress noisy pymongo connection/heartbeat debug logs
logging.getLogger("pymongo").setLevel(logging.WARNING)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await connect_to_mongo()
    yield
    # Shutdown
    await close_mongo_connection()


app = FastAPI(
    title=settings.APP_NAME,
    lifespan=lifespan,
    # Disable docs UI in production
    docs_url="/docs" if settings.DEBUG else None,
    redoc_url="/redoc" if settings.DEBUG else None,
)

# ── Rate Limiting ─────────────────────────────────────────────────────
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# ── CORS ──────────────────────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Request logging middleware ────────────────────────────────────────
@app.middleware("http")
async def log_requests(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = (time.perf_counter() - start) * 1000
    logger.info(
        "%s %s → %d (%.0fms)",
        request.method,
        request.url.path,
        response.status_code,
        elapsed_ms,
    )
    return response


# ── Fix Swagger UI file uploads (OpenAPI 3.1 compat) ──────────────────
def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
    schema = get_openapi(
        title=app.title,
        version=app.version,
        routes=app.routes,
    )
    for path in schema.get("components", {}).get("schemas", {}).values():
        for prop in path.get("properties", {}).values():
            if "contentMediaType" in prop:
                prop["format"] = "binary"
            items = prop.get("items", {})
            if "contentMediaType" in items:
                items["format"] = "binary"
    app.openapi_schema = schema
    return schema

app.openapi = custom_openapi


# ── Local file storage ────────────────────────────────────────────────
os.makedirs("storage/videos", exist_ok=True)
os.makedirs("storage/photos", exist_ok=True)
app.mount("/storage", StaticFiles(directory="storage"), name="storage")

# ── Admin dashboard static files ──────────────────────────────────────
admin_ui_path = os.path.join(os.path.dirname(__file__), "..", "..", "admin_ui")
app.mount("/static/admin", StaticFiles(directory=admin_ui_path), name="admin_static")

# ── Routers ───────────────────────────────────────────────────────────
app.include_router(admin.router, prefix=settings.API_V1_STR)
app.include_router(mobile.router, prefix=settings.API_V1_STR)
app.include_router(albums.router, prefix=settings.API_V1_STR)


@app.get("/")
async def root():
    return {"message": "Welcome to the Wedding Memory Platform API"}


@app.get("/admin")
async def admin_redirect():
    """Redirect /admin to the admin dashboard."""
    from fastapi.responses import RedirectResponse
    # Redirecting allows relative links like "style.css" to resolve correctly in the browser
    return RedirectResponse("/static/admin/index.html")
