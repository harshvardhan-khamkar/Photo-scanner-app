import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.openapi.utils import get_openapi
from .routers import admin, mobile, albums
from .database import connect_to_mongo, close_mongo_connection
from .config import settings
from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await connect_to_mongo()
    yield
    # Shutdown
    await close_mongo_connection()

app = FastAPI(
    title=settings.APP_NAME,
    lifespan=lifespan
)

# ── Fix Swagger UI file uploads (OpenAPI 3.1 compat) ──────────────────
def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
    schema = get_openapi(
        title=app.title,
        version=app.version,
        routes=app.routes,
    )
    # Patch: add format=binary wherever contentMediaType is used
    # so Swagger UI renders "Choose File" buttons instead of text inputs
    for path in schema.get("components", {}).get("schemas", {}).values():
        for prop in path.get("properties", {}).values():
            if "contentMediaType" in prop:
                prop["format"] = "binary"
            # Handle array items (e.g. List[UploadFile])
            items = prop.get("items", {})
            if "contentMediaType" in items:
                items["format"] = "binary"
    app.openapi_schema = schema
    return schema

app.openapi = custom_openapi

# ── Local file storage (dev) ──────────────────────────────────────────
os.makedirs("storage/videos", exist_ok=True)
os.makedirs("storage/photos", exist_ok=True)
app.mount("/storage", StaticFiles(directory="storage"), name="storage")

# Include Routers
app.include_router(admin.router, prefix=settings.API_V1_STR)
app.include_router(mobile.router, prefix=settings.API_V1_STR)
app.include_router(albums.router, prefix=settings.API_V1_STR)

@app.get("/")
async def root():
    return {"message": "Welcome to the Wedding Memory Platform API"}

