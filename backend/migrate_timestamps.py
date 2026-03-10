"""
Migrate album timestamps from M.SS format to standard seconds.

Before: start_time=1.5  means "1 min 5 sec"  (non-standard M.SS)
After:  start_time=65.0 means "65 seconds"    (standard seconds)

Run:  python migrate_timestamps.py
"""
import asyncio
from motor.motor_asyncio import AsyncIOMotorClient

MONGO_URI = "mongodb://localhost:27017"
DB_NAME = "wedding_platform"


def mss_to_seconds(val: float) -> float:
    """Convert M.SS (minutes.seconds) to total seconds.
    
    Examples:
        1.5   → 1 min  5 sec → 65.0
        2.58  → 2 min 58 sec → 178.0
        1.33  → 1 min 33 sec → 93.0
        1.31  → 1 min 31 sec → 91.0
        3.9   → 3 min  9 sec → 189.0
        3.3   → 3 min  3 sec → 183.0
    """
    val = round(val, 2)
    val_str = str(val)
    if '.' in val_str:
        parts = val_str.split('.')
        minutes = int(parts[0])
        seconds = int(parts[1])
    else:
        minutes = int(val_str)
        seconds = 0
    return float(minutes * 60 + seconds)


async def migrate():
    client = AsyncIOMotorClient(MONGO_URI)
    db = client[DB_NAME]
    collection = db["albums"]

    albums = await collection.find({}).to_list(length=None)
    print(f"Found {len(albums)} album(s) to migrate.\n")

    for album in albums:
        name = album.get("name", "unknown")
        album_id = album["_id"]
        frames = album.get("frames", [])
        updated_frames = []

        print(f"Album: {name} ({album_id})")
        for i, f in enumerate(frames):
            old_start = f.get("start_time", 0.0)
            old_end = f.get("end_time", 0.0)
            new_start = mss_to_seconds(old_start)
            new_end = mss_to_seconds(old_end)

            print(f"  Frame {i} ({f.get('photo_id', '?')}):")
            print(f"    start_time: {old_start} (M.SS) → {new_start}s")
            print(f"    end_time:   {old_end} (M.SS) → {new_end}s")

            f["start_time"] = new_start
            f["end_time"] = new_end
            updated_frames.append(f)

        await collection.update_one(
            {"_id": album_id},
            {"$set": {"frames": updated_frames}}
        )
        print(f"  ✅ Updated!\n")

    client.close()
    print("Migration complete.")


if __name__ == "__main__":
    asyncio.run(migrate())
