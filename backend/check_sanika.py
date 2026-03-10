"""
Inspect all albums and show their frame timestamps from MongoDB.
"""
import asyncio
from motor.motor_asyncio import AsyncIOMotorClient

MONGO_URI = "mongodb://localhost:27017"
DB_NAME = "wedding_platform"

async def check():
    client = AsyncIOMotorClient(MONGO_URI)
    db = client[DB_NAME]
    collection = db["albums"]

    albums = await collection.find({}).to_list(length=None)
    for album in albums:
        print(f"\n=== Album: {album.get('name')} (code={album.get('access_code')}) ===")
        frames = album.get("frames", [])
        if not frames:
            print("  No frames!")
        for i, f in enumerate(frames):
            start = f.get("start_time")
            end = f.get("end_time")
            print(f"  Frame {i} ({f.get('photo_id','?')}): start_time={repr(start)} ({type(start).__name__}), end_time={repr(end)} ({type(end).__name__})")

    client.close()

if __name__ == "__main__":
    asyncio.run(check())
