package com.weddingmemory.app.domain.repository

import android.graphics.Bitmap
import com.weddingmemory.app.domain.model.RecognitionResult
import kotlinx.coroutines.flow.Flow

/**
 * RecognitionRepository — domain contract for image recognition operations.
 *
 * Implementations in the data layer wire this to ML Kit, TFLite,
 * OpenCV, or any other engine — the domain layer is completely isolated.
 *
 * Threading contract: implementations are responsible for dispatching
 * to the correct thread. Use cases consume these APIs on [Dispatchers.Default]
 * via [DispatcherProvider].
 */
interface RecognitionRepository {

    // -------------------------------------------------------------------------
    // Engine lifecycle
    // -------------------------------------------------------------------------

    /**
     * Load and index all frame signatures for [albumId] into the
     * in-memory recognition engine.
     *
     * Must be called once after [AlbumRepository.getFrames] resolves,
     * before any [recognizeFrame] calls. Idempotent — safe to call
     * multiple times; the engine will skip frames already indexed.
     *
     * @return [Result.failure] if signatures cannot be loaded.
     */
    suspend fun loadSignatures(albumId: String): Result<Unit>

    /**
     * Release all in-memory resources held for [albumId].
     * Called when the user exits the scanner for that album.
     */
    suspend fun releaseSignatures(albumId: String)

    /**
     * Returns true if signatures for [albumId] are currently loaded
     * and the engine is ready to recognise frames.
     */
    fun isEngineReady(albumId: String): Boolean

    // -------------------------------------------------------------------------
    // Recognition
    // -------------------------------------------------------------------------

    /**
     * Run recognition on a single captured [imageData] against the
     * loaded signatures for [albumId].
     *
     * Returns a [Flow] so callers can observe intermediate [RecognitionResult.Scanning]
     * states before the terminal [RecognitionResult.Recognised] or
     * [RecognitionResult.Unrecognised] result arrives.
     *
     * @param albumId   The album whose frame signatures to search against.
     * @param imageData Raw image bytes (JPEG or NV21, depending on CameraX config).
     *                  The domain does not prescribe the format — the data-layer
     *                  adapter decides how to interpret this.
     */
    fun recognizeFrame(albumId: String, image: Bitmap): Flow<RecognitionResult>
}
