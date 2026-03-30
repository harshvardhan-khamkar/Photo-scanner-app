package com.weddingmemory.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.weddingmemory.app.data.recognition.EmbeddingEngine
import com.weddingmemory.app.domain.model.Frame
import com.weddingmemory.app.domain.model.RecognitionResult
import com.weddingmemory.app.domain.repository.AlbumRepository
import com.weddingmemory.app.domain.repository.RecognitionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RecognitionRepositoryImpl — production recognition engine.
 *
 * Pipeline per [recognizeFrame] call:
 * 1. Emit [RecognitionResult.Scanning]
 * 2. Extract float embedding from image bytes via [EmbeddingEngine]
 * 3. Compare against all stored frame embeddings for the album
 *    using cosine similarity (dot product of L2-normalised vectors)
 * 4. Emit [RecognitionResult.Recognised] when best score ≥ [MATCH_THRESHOLD],
 *    or [RecognitionResult.Unrecognised] otherwise.
 *
 * Stored embeddings are loaded during [loadSignatures]:
 *  - [Frame.imageSignature] is expected to be a Base64-encoded float array.
 *  - Frames with an unparseable signature get a zero-vector placeholder
 *    (matches nothing — prevents crashes during development).
 */
@Singleton
class RecognitionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumRepository: AlbumRepository,
) : RecognitionRepository {

    companion object {
        /**
         * Minimum cosine similarity for the top candidate to be considered a match.
         * Tune upward to reduce false positives; downward to reduce missed matches.
         */
        const val MATCH_THRESHOLD = 0.60f

        /**
         * Minimum gap between the best and second-best similarity scores.
         * Forces the engine to be unambiguous — a near-tie is treated as no-match.
         * Tune upward for stricter discrimination; downward if the album has
         * visually similar frames that legitimately score close together.
         */
        const val MIN_MARGIN = 0.15f

        /**
         * Warn when two stored frame embeddings are already extremely close.
         * This helps surface albums where two source photos are likely to confuse
         * the live recogniser before the user starts scanning.
         */
        private const val STORED_DUPLICATE_WARNING_THRESHOLD = 0.92f
    }

    // albumId → loaded EmbeddingEngine
    private val engines = mutableMapOf<String, EmbeddingEngine>()

    // albumId → list of (Frame, embedding) pairs
    private val albumEmbeddings = mutableMapOf<String, List<FrameEmbedding>>()

    // albumId → pre-allocated query buffer for zero-alloc per-frame inference
    private val queryBuffers = mutableMapOf<String, FloatArray>()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override suspend fun loadSignatures(albumId: String): Result<Unit> {
        return runCatching {
            val frames = albumRepository.getFrames(albumId).getOrThrow()
            Timber.d("RecognitionRepository: loading ${frames.size} frame embeddings for $albumId")

            albumEmbeddings[albumId] = frames.map { frame ->
                FrameEmbedding(frame, parseSignature(frame.imageSignature))
            }
            logPotentialNearDuplicates(albumId, albumEmbeddings[albumId].orEmpty())

            // Engine load is fail-safe: if the model asset is missing the scanner
            // still opens — recognizeFrame will return Unrecognised until the model
            // is present and the album is re-initialized.
            try {
                val engine = EmbeddingEngine(context).also { it.load() }
                engines[albumId] = engine
                queryBuffers[albumId] = FloatArray(EmbeddingEngine.EMBEDDING_DIM)
                Timber.d("RecognitionRepository: engine ready for $albumId")
            } catch (e: Exception) {
                Timber.w("RecognitionRepository: engine load failed — recognition disabled (${e.message})")
            }
        }
    }

    override suspend fun releaseSignatures(albumId: String) {
        engines.remove(albumId)?.close()
        albumEmbeddings.remove(albumId)
        queryBuffers.remove(albumId)
        Timber.d("RecognitionRepository: released engine for $albumId")
    }

    override fun isEngineReady(albumId: String): Boolean =
        engines.containsKey(albumId) && albumEmbeddings.containsKey(albumId)

    // -------------------------------------------------------------------------
    // Recognition
    // -------------------------------------------------------------------------

    override fun recognizeFrame(
        albumId: String,
        image: Bitmap,
    ): Flow<RecognitionResult> = flow {
        Timber.d("REAL RECOGNITION ACTIVE")
        emit(RecognitionResult.Scanning)
        val start = System.currentTimeMillis()

        val engine          = engines[albumId]
        val storedEmbeddings = albumEmbeddings[albumId]
        val queryBuffer     = queryBuffers[albumId]

        if (engine == null || storedEmbeddings == null || queryBuffer == null) {
            // Engine not loaded (model file missing) — degrade gracefully, don't crash scanner
            Timber.w("RecognitionRepository: engine not ready for $albumId — returning Unrecognised")
            emit(RecognitionResult.Unrecognised)
            return@flow
        }

        // Zero-allocation inference — result written into pre-allocated queryBuffer
        val orientationBuffer = FloatArray(EmbeddingEngine.EMBEDDING_DIM)
        val candidates = buildOrientationCandidates(image)
        val frameScores = mutableMapOf<String, Float>()

        try {
            for ((rotationDegrees, candidateImage) in candidates) {
                val targetBuffer = if (rotationDegrees == 0) queryBuffer else orientationBuffer
                val decoded = engine.extractEmbeddingInto(candidateImage, targetBuffer)
                if (!decoded) continue

                for (fe in storedEmbeddings) {
                    if (fe.embedding.isEmpty()) continue
                    val score = cosineSimilarity(fe.embedding, targetBuffer)

                    // Keep highest score per frame across all orientations
                    val current = frameScores[fe.frame.id] ?: 0f
                    if (score > current) frameScores[fe.frame.id] = score
                }
            }
        } finally {
            candidates
                .filter { it.first != 0 }
                .forEach { (_, candidateImage) -> candidateImage.recycle() }
        }

        // Now find best and second best from per-frame maximums
        val sorted = frameScores.entries.sortedByDescending { it.value }
        val bestEntry = sorted.getOrNull(0)
        val secondEntry = sorted.getOrNull(1)

        val bestScore = bestEntry?.value ?: 0f
        val secondBestScore = secondEntry?.value ?: 0f
        val bestEmbedding = storedEmbeddings.find { it.frame.id == bestEntry?.key }
        val topMatches = sorted
            .take(3)
            .joinToString(", ") { "${it.key}=%.4f".format(it.value) }
            .ifBlank { "none" }

        val latencyMs = System.currentTimeMillis() - start
        val margin    = bestScore - secondBestScore

        val frameIdStr = bestEmbedding?.frame?.id ?: "none"
        val matchStr = if (bestEmbedding != null && bestScore >= MATCH_THRESHOLD && margin >= MIN_MARGIN) "MATCH" else "NO_MATCH"

        Timber.d(
            "RecognitionRepository: best=%.4f second=%.4f margin=%.4f frameId=$frameIdStr thr=%.2f minMargin=%.2f top=[$topMatches] ${latencyMs}ms → $matchStr".format(
                bestScore, secondBestScore, margin, MATCH_THRESHOLD, MIN_MARGIN
            )
        )

        if (bestEmbedding != null
            && bestScore >= MATCH_THRESHOLD
            && margin    >= MIN_MARGIN
        ) {
            emit(
                RecognitionResult.Recognised(
                    frame      = bestEmbedding.frame,
                    confidence = bestScore,
                    latencyMs  = latencyMs,
                )
            )
        } else {
            emit(RecognitionResult.Unrecognised)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses a Base64-encoded float array from [Frame.imageSignature].
     *
     * Format: Base64( little-endian float32 × EMBEDDING_DIM )
     *
     * Returns an empty array if the signature is blank or unparseable —
     * the frame will never match, but it won't crash the engine.
     */
    private fun parseSignature(signature: String): FloatArray {
        if (signature.isBlank()) return FloatArray(0)
        return try {
            // Try standard Base64 first (Python's base64.b64encode uses +/ chars)
            val bytes = try {
                Base64.getDecoder().decode(signature)
            } catch (e: Exception) {
                // Fallback: URL-safe decoder (handles -_ chars)
                Base64.getUrlDecoder().decode(signature)
            }
            val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            FloatArray(bytes.size / 4) { buf.float }
        } catch (e: Exception) {
            Timber.w("RecognitionRepository: could not parse signature — skipping frame (${e.message})")
            FloatArray(0)
        }
    }

    /**
     * Cosine similarity of two L2-normalised vectors.
     *
     * Because both vectors are L2-normalised in [EmbeddingEngine],
     * cosine similarity == dot product — no division needed.
     * Result is clamped to [0, 1] (negative similarity treated as no match).
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(0f, 1f)
    }

    private fun buildOrientationCandidates(image: Bitmap): List<Pair<Int, Bitmap>> {
        return listOf(
            0 to image,
            90 to image.rotate(90f),
            270 to image.rotate(270f),
        )
    }

    private fun Bitmap.rotate(rotationDegrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun logPotentialNearDuplicates(albumId: String, embeddings: List<FrameEmbedding>) {
        if (embeddings.size < 2) return

        for (i in 0 until embeddings.lastIndex) {
            val left = embeddings[i]
            if (left.embedding.isEmpty()) continue

            for (j in i + 1 until embeddings.size) {
                val right = embeddings[j]
                if (right.embedding.isEmpty()) continue

                val score = cosineSimilarity(left.embedding, right.embedding)
                if (score >= STORED_DUPLICATE_WARNING_THRESHOLD) {
                    Timber.w(
                        "RecognitionRepository: album=%s has very similar stored frames %s and %s (score=%.4f)",
                        albumId,
                        left.frame.id,
                        right.frame.id,
                        score
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private data class FrameEmbedding(
        val frame: Frame,
        val embedding: FloatArray,
    )
}
