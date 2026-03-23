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
        # ── Users indexes ─────────────────────────────────────────
        await db_connection.db.users.create_index(
            "email", unique=True, background=True
        )
        logger.info("Ensured unique index on users.email")

        # ── Albums indexes ────────────────────────────────────────
        # NOTE: We intentionally do NOT index access_code because it is
        # now stored as a bcrypt hash (unlock uses a full-scan verify).
        await db_connection.db.albums.create_index(
            "owner_id", background=True
        )
        logger.info("Ensured index on albums.owner_id")

        await db_connection.db.albums.create_index(
            "reset_token", background=True, sparse=True
        )
        logger.info("Ensured index on albums.reset_token")

        # Drop the old unique index on access_code if it exists
        existing_indexes = await db_connection.db.albums.index_information()
        for idx_name, idx_info in existing_indexes.items():
            keys = [k for k, _ in idx_info.get("key", [])]
            if keys == ["access_code"]:
                await db_connection.db.albums.drop_index(idx_name)
                logger.info("Dropped legacy unique index on albums.access_code (%s)", idx_name)
                break
    except Exception as e:
        # Don't crash if index creation fails
        logger.warning("Index creation warning: %s", e)


async def close_mongo_connection():
    if db_connection.client:
        db_connection.client.close()
        logger.info("MongoDB connection closed")


def get_database():
    return db_connection.db
