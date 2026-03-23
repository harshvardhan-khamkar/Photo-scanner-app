# Image Recognition System Overview

The Wedding Memory platform uses a highly optimized, on-device image recognition system. It allows guests to point their phone camera at a physical wedding photo, instantly identify which photo it is, and trigger the associated video segment.

This document details how the system works across the Backend and the Android App.

## 1. The Core Model: MobileNetV2 (TFLite)

The core engine relies on **MobileNetV2**, specifically a version trained as a **feature extractor** rather than a classifier. 

*   **Format:** TensorFlow Lite (`.tflite`)
*   **Input:** `1 x 224 x 224 x 3` (A 224x224 RGB image, normalized to `[-1, 1]`)
*   **Output:** `1 x 1280` (A 1280-dimensional global average pooled feature vector, float32)
*   **Purpose:** Instead of saying "This is a dog" or "This is a tree", it outputs an array of 1280 numbers. These numbers establish a mathematical "fingerprint" or **embedding** representing the visual composition, colors, and textures of the image.

We use **identical models** on both the Backend (Python) and the Android App (Kotlin) to ensure the generated fingerprints match perfectly.

---

## 2. Admin Upload Flow (Backend Pipeline)

When an admin uploads a new album, they provide video files and photo frames. 

1.  **Image Upload:** Admin uploads `Mehndi.jpg` (along with its video timestamps).
2.  **Preprocessing:** The backend Python script ([utils/embeddings.py](file:///c:/Users/admin/Desktop/sanika%20wedding/backend/app/utils/embeddings.py)) opens the image, resizes it to 224x224 pixels using high-quality Lanczos resampling, and converts the pixel values from `0..255` to `-1.0..1.0`.
3.  **Inference:** The tensor is passed into the TFLite model, outputting the 1280-dimension vector.
4.  **L2 Normalization:** The vector is mathematically normalized (its length is scaled to exactly `1.0`). This is crucial because it allows us to compare images using a simple "dot product" (Cosine Similarity) later.
5.  **Storage:** The 1280 floats are packed into bytes, encoded in Base64, and saved in MongoDB as the `image_signature` for that specific frame.

---

## 3. Guest Scanning Flow (Android Pipeline)

When a guest opens the app and enters the album code, the app downloads all the Base64 `image_signatures` for that album and caches them locally.

When they open the scanner:

1.  **Camera Feed:** CameraX streams frames at ~30 FPS. To save battery, the app throttles processing to one frame every 500 milliseconds (2 FPS).
2.  **Preprocessing:** The raw YUV camera frame is converted to a Bitmap, cropped, resized to 224x224, and normalized to `[-1.0, 1.0]`.
3.  **Orientation Handling:** Physical photos might be rotated. The app generates candidates at 0°, 90°, and 270° to ensure robust matching even if the phone is held sideways. 
4.  **Inference:** The TFLite interpreter (accelerated by XNNPACK for realtime performance) generates a 1280-dimension vector for the live camera frame.
5.  **L2 Normalization:** The live vector is L2-normalized.

---

## 4. How Matching Works (Cosine Similarity)

The magic happens when the app compares the **Live Camera Fingerprint** against all the **Stored Frame Fingerprints**.

It uses **Cosine Similarity** (since both vectors are L2-normalized, this is just a mathematically fast dot product). The score ranges from `-1.0` (completely opposite) to `1.0` (identical).

### The Two Security Gates

To prevent false positives (like the issues you experienced with `Sangeet.jpg` playing randomly, or `Mehndi` crossing with `Mangalsutr`), the app employs two threshold gates in [RecognitionRepositoryImpl.kt](file:///c:/Users/admin/Desktop/sanika%20wedding/weddingmemory/app/src/main/java/com/weddingmemory/app/data/repository/RecognitionRepositoryImpl.kt).

#### Gate 1: `MATCH_THRESHOLD`
*   **What it is:** The minimum similarity score required to declare a match.
*   **The Issue:** It was previously set to `0.50`. Because 0.50 is quite low for MobileNetV2 embeddings, pointing the camera at a random wall or blurry object might accidentally yield a similarity of > 0.50 with `Sangeet.jpg`, triggering autoplay.
*   **The Fix:** Increased to `0.75`. Now, the live camera feed must look *very substantially* like the target photo to trigger.

#### Gate 2: `MIN_MARGIN`
*   **What it is:** The required mathematical distance between the **top match** and the **second-best match**.
*   **The Issue:** It was previously set to `0.05`. If `Mehndi.jpg` and `Mangalsutr.jpg` both feature people wearing red with bright lighting, they might look similar to the AI. If the camera sees `Mehndi`, it might score `0.82` for Mehndi and `0.79` for Mangalsutr. With a margin of `0.05`, `0.82 - 0.79 = 0.03`, which is below the margin. If it fluctuates on the next frame to `0.80 Mangalsutr` and `0.79 Mehndi`, it crosses the wires.
*   **The Fix:** Increased to `0.15`. This prevents "guessing". If the engine sees a photo and it looks `0.85` like `Mehndi` but `0.75` like `Mangalsutr` (margin `0.10`), it will **reject the match** and wait for the user to hold the camera steadier or get closer until the score unambiguously identifies exactly *one* photo.

When both gates are cleared, the app confirms the match and navigates to the video player, starting playback at the precise timestamp provided by the backend.
