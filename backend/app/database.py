from motor.motor_asyncio import AsyncIOMotorClient
from .config import settings

class Database:
    client: AsyncIOMotorClient = None
    db = None

db_connection = Database()

async def connect_to_mongo():
    db_connection.client = AsyncIOMotorClient(settings.MONGODB_URI)
    db_connection.db = db_connection.client[settings.DATABASE_NAME]
    print(f"Connected to MongoDB: {settings.DATABASE_NAME}")

async def close_mongo_connection():
    if db_connection.client:
        db_connection.client.close()
        print("MongoDB connection closed")

def get_database():
    return db_connection.db
