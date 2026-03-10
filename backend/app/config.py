from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

class Settings(BaseSettings):
    # App Settings
    APP_NAME: str = "Wedding Memory Platform"
    DEBUG: bool = True
    API_V1_STR: str = "/api/v1"

    # MongoDB Settings
    MONGODB_URI: str = "mongodb://localhost:27017"
    DATABASE_NAME: str = "wedding_platform"

    # AWS Settings
    AWS_ACCESS_KEY_ID: Optional[str] = None
    AWS_SECRET_ACCESS_KEY: Optional[str] = None
    AWS_REGION: str = "us-east-1"
    S3_BUCKET_NAME: str = "wedding-memories-storage"
    CLOUDFRONT_DOMAIN: str = "https://cdn.example.com"

    # Security
    SECRET_KEY: str = "super-secret-key-change-me"
    
    model_config = SettingsConfigDict(env_file=".env", case_sensitive=True)

settings = Settings()
