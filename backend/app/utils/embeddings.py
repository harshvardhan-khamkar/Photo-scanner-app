import logging
import numpy as np
from PIL import Image
try:
    import tflite_runtime.interpreter as tflite
except ImportError:
    try:
        import tensorflow.lite as tflite
    except ImportError:
        tflite = None

import base64 
import os
from ..config import settings

logger = logging.getLogger(__name__)

class EmbeddingGenerator:
    def __init__(self, model_path: str = "app/resources/models/embedding_model.tflite"):
        if not tflite:
            raise ImportError("Neither tflite-runtime nor tensorflow is installed. Please install one of them.")
        
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"TFLite model not found at {model_path}. Please download it from TFHub.")
        
        self.interpreter = tflite.Interpreter(model_path=model_path)
        self.interpreter.allocate_tensors()
        
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()
        
        # Expected input shape (1, 224, 224, 3)
        self.input_shape = self.input_details[0]['shape'][1:3]

    def generate_embedding(self, image_path: str) -> str:
        """
        Generate a Base64 encoded L2-normalized embedding for a given image.
        """
        # 1. Load image
        img = Image.open(image_path).convert('RGB')

        # Issue #10 — Reject images below minimum resolution
        if img.width < 100 or img.height < 100:
            logger.warning(
                "Image too small (%dx%d) — minimum 100x100 required: %s",
                img.width, img.height, image_path,
            )
            raise ValueError(
                f"Image too small ({img.width}x{img.height}), "
                f"minimum 100x100 required: {image_path}"
            )

        # Issue #2 — Center-crop to square before resize (matches Android)
        w, h = img.size
        short = min(w, h)
        left = (w - short) // 2
        top  = (h - short) // 2
        img = img.crop((left, top, left + short, top + short))

        # Issue #1 — Use BILINEAR to match Android's bilinear filtering
        img = img.resize(self.input_shape, Image.Resampling.BILINEAR)
        
        # 2. Preprocess: (pixel / 127.5) - 1.0
        input_data = np.array(img, dtype=np.float32)
        input_data = (input_data / 127.5) - 1.0
        input_data = np.expand_dims(input_data, axis=0) # Add batch dimension
        
        # 3. Run Inference
        self.interpreter.set_tensor(self.input_details[0]['index'], input_data)
        self.interpreter.invoke()
        
        # 4. Extract embedding (feature vector)
        embedding = self.interpreter.get_tensor(self.output_details[0]['index'])[0]
        
        # 5. L2 Normalize
        norm = np.linalg.norm(embedding)
        if norm > 0:
            embedding = embedding / norm
            
        # 6. Encode as Base64 (standard alphabet)
        # Issue #5 — Explicit little-endian float32 byte order
        embedding_bytes = embedding.astype('<f4').tobytes()
        # Issue #4 — Use standard Base64 (+/) consistently
        encoded = base64.b64encode(embedding_bytes).decode('utf-8')
        
        return encoded

# Singleton instance
try:
    embedding_gen = EmbeddingGenerator()
except Exception as e:
    print(f"Warning: EmbeddingGenerator could not be initialized: {e}")
    embedding_gen = None
