from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional, List

class Settings(BaseSettings):
    # App Settings
    APP_NAME: str = "Wedding Memory Platform"
    DEBUG: bool = False
    API_V1_STR: str = "/api/v1"

    # MongoDB Settings
    MONGODB_URI: str = "mongodb://localhost:27017"
    DATABASE_NAME: str = "wedding_platform"

    # AWS Settings (for future S3 integration)
    AWS_ACCESS_KEY_ID: Optional[str] = None
    AWS_SECRET_ACCESS_KEY: Optional[str] = None
    AWS_REGION: str = "us-east-1"
    S3_BUCKET_NAME: str = "wedding-memories-storage"
    CLOUDFRONT_DOMAIN: str = "https://cdn.example.com"

    # Security
    SECRET_KEY: str = "CHANGE-ME-generate-a-real-key"
    ADMIN_API_KEY: str = "CHANGE-ME-generate-a-real-admin-key"

    # SMTP / Email
    SMTP_HOST: Optional[str] = None
    SMTP_PORT: Optional[int] = 587
    SMTP_USER: Optional[str] = None
    SMTP_PASSWORD: Optional[str] = None
    SMTP_FROM_EMAIL: Optional[str] = None

    # CORS
    ALLOWED_ORIGINS: str = "*"

    # Storage backend: "local" or "s3" (only "local" is implemented for now)
    STORAGE_BACKEND: str = "local"

    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True)

    @property
    def cors_origins(self) -> List[str]:
        """Parse ALLOWED_ORIGINS as a comma-separated list."""
        if self.ALLOWED_ORIGINS == "*":
            return ["*"]
        return [o.strip() for o in self.ALLOWED_ORIGINS.split(",") if o.strip()]

settings = Settings()
