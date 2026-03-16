import logging
from motor.motor_asyncio import AsyncIOMotorClient
from .config import settings

logger = logging.getLogger(__name__)


class Database:
    client: AsyncIOMotorClient = None
    db = None


db_connection = Database()


async def connect_to_mongo():
    """Connect to MongoDB with connection pooling."""
    db_connection.client = AsyncIOMotorClient(
        settings.MONGODB_URI,
        maxPoolSize=50,
        minPoolSize=5,
        serverSelectionTimeoutMS=5000,
    )
    db_connection.db = db_connection.client[settings.DATABASE_NAME]
    logger.info("Connected to MongoDB: %s", settings.DATABASE_NAME)

    # Ensure indexes (idempotent — safe to run every startup)
    try:
        await db_connection.db.albums.create_index(
            "access_code", unique=True, background=True
        )
        logger.info("Ensured index on albums.access_code")
    except Exception as e:
        # Don't crash if index creation fails (e.g. duplicate access codes exist)
        logger.warning("Index creation warning: %s", e)


async def close_mongo_connection():
    if db_connection.client:
        db_connection.client.close()
        logger.info("MongoDB connection closed")


def get_database():
    return db_connection.db
