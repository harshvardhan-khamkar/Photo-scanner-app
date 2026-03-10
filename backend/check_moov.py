import struct, os, sys

path = "storage/videos/39d36412be2a44f2ae6e6e2e0d1746f3.mp4"
fsize = os.path.getsize(path)
print(f"File size: {fsize:,} bytes ({fsize/(1024*1024):.1f} MB)")

with open(path, "rb") as f:
    offset = 0
    boxes = []
    while offset < fsize:
        f.seek(offset)
        header = f.read(8)
        if len(header) < 8:
            break
        size = struct.unpack(">I", header[:4])[0]
        box_type = header[4:8].decode("ascii", errors="replace")
        if size == 1:
            ext = f.read(8)
            size = struct.unpack(">Q", ext)[0]
        if size == 0:
            size = fsize - offset
        boxes.append((box_type, offset, size))
        print(f"  [{box_type}] offset={offset:>12,}  size={size:>12,}")
        offset += size

moov_pos = next((i for i, (t, _, _) in enumerate(boxes) if t == "moov"), None)
mdat_pos = next((i for i, (t, _, _) in enumerate(boxes) if t == "mdat"), None)

if moov_pos is not None and mdat_pos is not None:
    if moov_pos < mdat_pos:
        print("\n✅ moov is BEFORE mdat — faststart is OK")
    else:
        print("\n❌ moov is AFTER mdat — NOT faststart! ExoPlayer can't seek!")
        print("   Fix: re-encode with ffmpeg -movflags +faststart")
else:
    print(f"\nmoov_pos={moov_pos} mdat_pos={mdat_pos}")
