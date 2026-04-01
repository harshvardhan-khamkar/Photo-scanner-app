package com.weddingmemory.app.data.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * EmbeddingEngine — singleton TFLite wrapper for MobileNetV2 feature extraction.
 *
 * Model contract (MobileNetV2 float32 feature extractor):
 *   Input  : [1, 224, 224, 3]  — float32, normalised to [-1, 1] via (pixel / 127.5) - 1
 *   Output : [1, 1280]         — global average pooled feature vector
 *
 * Performance design
 * ──────────────────
 * All buffers are allocated ONCE in [load] and reused every inference:
 *   • [scaledBitmap]  — 224×224 ARGB_8888 Bitmap reused across frames
 *   • [pixelBuffer]   — IntArray(224×224) for getPixels()
 *   • [inputBuffer]   — direct ByteBuffer (1×224×224×3×4 bytes = 602 KB)
 *   • [outputBuffer]  — Array(1){FloatArray(1280)} consumed by interpreter
 *
 * The only per-inference allocation is the L2-normalised result FloatArray
 * returned to the caller — unavoidable when the result must be stored.
 * Query embeddings (not stored) can avoid this — see [extractEmbeddingInto].
 *
 * ⚠️  Place your model at: app/src/main/assets/embedding_model.tflite
 */
class EmbeddingEngine(private val context: Context) : Closeable {

    companion object {
        const val MODEL_ASSET   = "embedding_model.tflite"
        const val INPUT_SIZE    = 224
        const val EMBEDDING_DIM = 1280      // MobileNetV2 output dim
        private const val QUERY_CENTER_CROP_FRACTION = 0.80f

        private const val NUM_CHANNELS   = 3
        private const val BYTES_PER_FLOAT = 4

        /** Total bytes for one input tensor: 1 × 224 × 224 × 3 × 4 */
        private val INPUT_BUFFER_BYTES =
            1 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * BYTES_PER_FLOAT // 602,112
    }

    // -------------------------------------------------------------------------
    // Singleton interpreter — set once in load(), released in close()
    // -------------------------------------------------------------------------

    @Volatile private var interpreter: Interpreter? = null

    // -------------------------------------------------------------------------
    // Pre-allocated buffers — initialised in load(), reused every inference
    // -------------------------------------------------------------------------

    /** Reusable scaled bitmap — avoids one allocation per frame */
    private lateinit var scaledBitmap: Bitmap

    /** Canvas bound to scaledBitmap — draws source frame into 224×224 */
    private lateinit var scaledCanvas: Canvas

    /** Paint with bilinear filter for quality scaling */
    private val scaledPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Packed ARGB pixel buffer — reused by getPixels() */
    private lateinit var pixelBuffer: IntArray

    /** Direct ByteBuffer for TFLite input — MUST be native-order */
    private lateinit var inputBuffer: ByteBuffer

    /** TFLite output buffer matching [1, EMBEDDING_DIM] */
    private val outputBuffer = Array(1) { FloatArray(EMBEDDING_DIM) }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Loads the TFLite model from assets and initialises all pre-allocated buffers.
     * Safe to call multiple times — returns immediately if already loaded.
     *
     * @throws IllegalStateException if the model asset is missing.
     */
    fun load() {
        if (interpreter != null) return

        val model = loadModelFromAssets()
        val options = Interpreter.Options().apply {
            numThreads = 2        // 2 inference threads — balanced for real-time use
            useXNNPACK = true     // XNNPACK delegate: ~2× throughput on ARM
        }
        interpreter = Interpreter(model, options)

        // Verify tensor shapes match MobileNetV2 feature extractor contract
        // Expected → Input: [1, 224, 224, 3]   Output: [1, 1280]
        // If Output shows [1, 1001] → wrong model (classifier, not feature extractor)
        Timber.d("Model input  shape: ${interpreter!!.getInputTensor(0).shape().contentToString()}")
        Timber.d("Model output shape: ${interpreter!!.getOutputTensor(0).shape().contentToString()}")

        // Allocate buffers once — reuse on every subsequent inference
        scaledBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        scaledCanvas = Canvas(scaledBitmap)
        pixelBuffer  = IntArray(INPUT_SIZE * INPUT_SIZE)
        inputBuffer  = ByteBuffer
            .allocateDirect(INPUT_BUFFER_BYTES)
            .order(ByteOrder.nativeOrder())

        Timber.d("EmbeddingEngine: loaded MobileNetV2 (input=${INPUT_SIZE}px, output=$EMBEDDING_DIM)")

        // Warm up — run one dummy inference with a zero tensor so that
        // XNNPACK kernels are compiled and JIT is primed before the first
        // real frame arrives. Happens on Dispatchers.IO inside
        // InitializeAlbumUseCase — never on the UI thread.
        warmUp()
    }

    /** Releases the TFLite interpreter and native memory. */
    override fun close() {
        interpreter?.close()
        interpreter = null
        Timber.d("EmbeddingEngine: released")
    }

    /**
     * Runs a single inference on a zeroed input tensor.
     * Results are discarded — the sole purpose is to force XNNPACK
     * kernel compilation and JIT warm-up so that the first real
     * recognition frame is not penalised by cold-start latency.
     *
     * Called once at the end of [load], which itself is called from
     * [RecognitionRepositoryImpl.loadSignatures] on [Dispatchers.IO].
     */
    private fun warmUp() {
        val interp = interpreter ?: return
        val warmupOutput = Array(1) { FloatArray(EMBEDDING_DIM) }

        // Reuse the pre-allocated inputBuffer filled with zeros
        inputBuffer.rewind()
        repeat(INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS) { inputBuffer.putFloat(0f) }
        inputBuffer.rewind()

        val t0 = System.currentTimeMillis()
        interp.run(inputBuffer, warmupOutput)
        Timber.d("EmbeddingEngine: warmup done in ${System.currentTimeMillis() - t0}ms")

        // Leave inputBuffer position at 0 ready for the first real inference
        inputBuffer.rewind()
    }

    // -------------------------------------------------------------------------
    // Inference — returns a NEW FloatArray (safe to store in albumEmbeddings)
    // -------------------------------------------------------------------------

    /**
     * Decodes [imageBytes], runs inference, and returns an L2-normalised
     * embedding as a new [FloatArray].
     *
     * Use this when the result will be stored (e.g. [loadSignatures]).
     *
     * @return L2-normalised FloatArray of size [EMBEDDING_DIM], or null on decode failure.
     */
    fun extractEmbedding(imageBytes: ByteArray): FloatArray? {
        val interp = interpreter ?: error("EmbeddingEngine not loaded — call load() first")
        val src = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: run {
            Timber.w("EmbeddingEngine: failed to decode ${imageBytes.size} bytes")
            return null
        }
        return runInference(interp, src).copyOf()   // safe copy for storage
    }

    /**
     * Overload for callers that already have a [Bitmap].
     * Returns a new FloatArray — safe to store.
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val interp = interpreter ?: error("EmbeddingEngine not loaded — call load() first")
        return runInference(interp, bitmap).copyOf()
    }

    /**
     * Zero-allocation variant — writes the L2-normalised embedding directly
     * into [dest] without creating a new array.
     *
     * Use this inside the scanner hot-loop where the result is consumed
     * immediately and not stored. [dest] must be size [EMBEDDING_DIM].
     */
    fun extractEmbeddingInto(image: Bitmap, dest: FloatArray): Boolean {
        require(dest.size == EMBEDDING_DIM) {
            "dest must be FloatArray($EMBEDDING_DIM), got ${dest.size}"
        }
        val interp = interpreter ?: return false
        val result = runInference(interp, image)
        result.copyInto(dest)
        return true
    }

    // -------------------------------------------------------------------------
    // Core inference — all buffer reuse happens here
    // -------------------------------------------------------------------------

    /**
     * Scales [source] into the pre-allocated [scaledBitmap], builds the input
     * [ByteBuffer], runs the interpreter, and returns the L2-normalised
     * vector from [outputBuffer][0].
     *
     * ⚠️  The returned FloatArray IS [outputBuffer][0] — do NOT store it directly.
     *    Call [copyOf] if you need to keep the result (see [extractEmbedding]).
     */
    // Issue #7 — Synchronized to protect shared mutable buffers from concurrent access
    @Synchronized
    private fun runInference(interp: Interpreter, source: Bitmap): FloatArray {
        // Step 1 — Scale source bitmap into pre-allocated 224×224 canvas
        val destRect = RectF(0f, 0f, INPUT_SIZE.toFloat(), INPUT_SIZE.toFloat())
        val sourceRect = buildCenterCropRect(source.width, source.height)
        scaledCanvas.drawBitmap(source, sourceRect, destRect, scaledPaint)

        // Step 2 — Extract packed ARGB pixels into pre-allocated IntArray
        scaledBitmap.getPixels(pixelBuffer, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Step 3 — Encode pixels into pre-allocated direct ByteBuffer
        //          Normalization: (channel / 127.5f) - 1f  → range [-1, 1]
        inputBuffer.rewind()
        for (pixel in pixelBuffer) {
            val r = (pixel ushr 16 and 0xFF)
            val g = (pixel ushr 8  and 0xFF)
            val b = (pixel          and 0xFF)
            inputBuffer.putFloat(r / 127.5f - 1f)
            inputBuffer.putFloat(g / 127.5f - 1f)
            inputBuffer.putFloat(b / 127.5f - 1f)
        }

        // Step 4 — Run inference (nanoTime for sub-millisecond precision)
        val inferenceStart = System.nanoTime()
        interp.run(inputBuffer, outputBuffer)
        val inferenceMs = (System.nanoTime() - inferenceStart) / 1_000_000L
        Timber.d("TFLite inference time: ${inferenceMs} ms")

        // Diagnostic: pre-normalization L2 norm (expected: ~10–30 for MobileNetV2)
        val vector = outputBuffer[0]
        var sumSqPre = 0f
        for (x in vector) sumSqPre += x * x
        val preNorm = Math.sqrt(sumSqPre.toDouble()).toFloat()
        Timber.d("EmbeddingEngine: pre-norm=%.4f".format(preNorm))

        // Step 5 — L2-normalise in-place within outputBuffer[0]
        val result = l2NormaliseInPlace(vector)

        // Diagnostic: post-normalization L2 norm (expected: ~1.0)
        var sumSqPost = 0f
        for (x in result) sumSqPost += x * x
        val postNorm = Math.sqrt(sumSqPost.toDouble()).toFloat()
        Timber.d("EmbeddingEngine: post-norm=%.6f  (Δ from 1.0 = %.6f)".format(postNorm, Math.abs(1f - postNorm)))

        return result
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun loadModelFromAssets(): MappedByteBuffer {
        val fd = try {
            context.assets.openFd(MODEL_ASSET)
        } catch (e: Exception) {
            throw IllegalStateException(
                "TFLite model not found: assets/$MODEL_ASSET\n" +
                "Place the MobileNetV2 float32 feature extractor at " +
                "app/src/main/assets/$MODEL_ASSET",
                e
            )
        }
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength,
        )
    }

    /**
     * L2-normalises [v] in-place.
     * Returns [v] itself (same reference) so no extra allocation.
     * If the vector is zero-length or all-zero, returns it unchanged (degenerate case).
     */
    private fun l2NormaliseInPlace(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) sumSq += x * x
        val norm = Math.sqrt(sumSq.toDouble()).toFloat()
        if (norm < 1e-10f) return v
        for (i in v.indices) v[i] /= norm
        return v
    }

    /**
     * Bias recognition toward the centre of the camera frame so the live query
     * better matches the clean uploaded photo embedding and ignores more of the
     * surrounding room, hands, borders, and table background.
     */
    private fun buildCenterCropRect(width: Int, height: Int): Rect {
        val cropSize = (minOf(width, height) * QUERY_CENTER_CROP_FRACTION)
            .toInt()
            .coerceAtLeast(1)
        val left = ((width - cropSize) / 2).coerceAtLeast(0)
        val top = ((height - cropSize) / 2).coerceAtLeast(0)
        return Rect(left, top, left + cropSize, top + cropSize)
    }
}
