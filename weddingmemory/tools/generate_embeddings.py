#!/usr/bin/env python3
"""
generate_embeddings.py
──────────────────────
Runs MobileNetV2 TFLite inference on a folder of images and outputs
JSON-formatted Base64 embeddings ready to paste into your album's
frame `imageSignature` field.

Normalization matches Android EmbeddingEngine exactly:
    normalized = (pixel_channel / 127.5) - 1.0   → range [-1, 1]

Base64 encoding uses URL-safe alphabet (no padding stripped) because
Android RecognitionRepositoryImpl uses Base64.getUrlDecoder().

Usage:
    python generate_embeddings.py --model embedding_model.tflite --images ./photos

Output:
    [
      { "file": "photo1.jpg", "embedding": "Base64StringHere" },
      ...
    ]

Requirements:
    pip install tflite-runtime numpy Pillow
    (or use full TensorFlow: pip install tensorflow numpy Pillow)
"""

import argparse
import base64
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# ---------------------------------------------------------------------------
# TFLite interpreter — prefer lightweight tflite-runtime, fall back to TF
# ---------------------------------------------------------------------------
try:
    import tflite_runtime.interpreter as tflite
    Interpreter = tflite.Interpreter
except ImportError:
    try:
        import tensorflow as tf
        Interpreter = tf.lite.Interpreter
    except ImportError:
        sys.exit(
            "ERROR: Install tflite-runtime or tensorflow.\n"
            "  pip install tflite-runtime numpy Pillow"
        )

# ---------------------------------------------------------------------------
# Constants — must match EmbeddingEngine.kt
# ---------------------------------------------------------------------------
INPUT_SIZE    = 224          # pixels
EMBEDDING_DIM = 1280         # MobileNetV2 global average pool output
PIXEL_MEAN    = 127.5        # (pixel / PIXEL_MEAN) - 1.0  → [-1, 1]
IMAGE_EXTS    = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


# ---------------------------------------------------------------------------
# Core functions
# ---------------------------------------------------------------------------

def load_interpreter(model_path: str) -> Interpreter:
    """Load and allocate the TFLite interpreter."""
    interp = Interpreter(model_path=model_path)
    interp.allocate_tensors()
    return interp


def preprocess(image_path: Path) -> np.ndarray:
    """
    Load and preprocess one image into a [1, 224, 224, 3] float32 tensor.

    Steps mirror EmbeddingEngine.kt:
      1. Open in RGB (strip alpha if present)
      2. Resize to 224×224 with BILINEAR filter
      3. Normalize: (channel / 127.5) - 1.0
      4. Add batch dimension
    """
    img = Image.open(image_path).convert("RGB")
    img = img.resize((INPUT_SIZE, INPUT_SIZE), Image.BILINEAR)

    arr = np.array(img, dtype=np.float32)   # shape [224, 224, 3]
    arr = arr / 127.5 - 1.0                 # normalize to [-1, 1]
    arr = np.expand_dims(arr, axis=0)        # shape [1, 224, 224, 3]
    return arr


def run_inference(interp: Interpreter, tensor: np.ndarray) -> np.ndarray:
    """Run one TFLite inference and return the raw output vector."""
    input_details  = interp.get_input_details()
    output_details = interp.get_output_details()

    interp.set_tensor(input_details[0]["index"], tensor)
    interp.invoke()

    output = interp.get_tensor(output_details[0]["index"])  # shape [1, 1280]
    return output[0]                                         # shape [1280]


def l2_normalize(v: np.ndarray) -> np.ndarray:
    """L2-normalise a 1-D vector. Returns zero vector if norm < 1e-10."""
    norm = np.linalg.norm(v)
    if norm < 1e-10:
        return v
    return v / norm


def to_base64(embedding: np.ndarray) -> str:
    """
    Encode a float32 numpy array as URL-safe Base64 (no newlines).

    Byte order: little-endian float32 — matches Android's:
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    Alphabet: URL-safe — matches Android's Base64.getUrlDecoder().
    """
    raw_bytes = embedding.astype("<f4").tobytes()          # little-endian float32
    return base64.urlsafe_b64encode(raw_bytes).decode("ascii")


def verify_normalization(embedding: np.ndarray, filename: str) -> None:
    """Print a quick sanity check — post-norm magnitude should be ~1.0."""
    norm = np.linalg.norm(embedding)
    print(
        f"  [check] {filename}: post-norm={norm:.6f}  "
        f"({'✓ OK' if abs(norm - 1.0) < 0.001 else '✗ FAILED — check model'})",
        file=sys.stderr,
    )


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate Base64 TFLite embeddings from a folder of images."
    )
    parser.add_argument(
        "--model",
        required=True,
        metavar="PATH",
        help="Path to embedding_model.tflite",
    )
    parser.add_argument(
        "--images",
        required=True,
        metavar="DIR",
        help="Directory containing .jpg / .png / .webp images",
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Print post-normalization magnitude check to stderr",
    )
    args = parser.parse_args()

    model_path  = Path(args.model)
    images_dir  = Path(args.images)

    if not model_path.is_file():
        sys.exit(f"ERROR: model not found: {model_path}")
    if not images_dir.is_dir():
        sys.exit(f"ERROR: images dir not found: {images_dir}")

    # Collect image files
    image_files = sorted(
        p for p in images_dir.iterdir()
        if p.suffix.lower() in IMAGE_EXTS
    )
    if not image_files:
        sys.exit(f"ERROR: no images ({', '.join(IMAGE_EXTS)}) found in {images_dir}")

    print(f"Loading model:  {model_path}", file=sys.stderr)
    print(f"Images found:   {len(image_files)}", file=sys.stderr)
    print("", file=sys.stderr)

    interp = load_interpreter(str(model_path))

    results = []
    for img_path in image_files:
        try:
            tensor    = preprocess(img_path)
            raw_vec   = run_inference(interp, tensor)
            embedding = l2_normalize(raw_vec)

            if args.verify:
                verify_normalization(embedding, img_path.name)

            results.append({
                "file":      img_path.name,
                "embedding": to_base64(embedding),
            })
        except Exception as exc:
            print(f"  [skip] {img_path.name}: {exc}", file=sys.stderr)

    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
