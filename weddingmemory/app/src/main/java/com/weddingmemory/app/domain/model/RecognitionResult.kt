package com.weddingmemory.app.domain.model

/**
 * RecognitionResult — the outcome of running the recognition engine
 * against one captured camera frame.
 *
 * Sealed so every call-site is forced to handle all outcomes.
 *
 * Typical state machine:
 *   [Scanning] → [Recognised] or [Unrecognised] or [Error]
 */
sealed class RecognitionResult {

    /**
     * The engine is actively processing — emitted while analysis is in-flight.
     * Consumers can show a scanning indicator.
     */
    object Scanning : RecognitionResult()

    /**
     * A match was found in the album.
     *
     * @param frame      The matched [Frame] — contains [Frame.videoUrl] for playback.
     * @param confidence Similarity score in the range [0.0, 1.0].
     *                   The threshold policy lives in the recognition engine, not here.
     * @param latencyMs  Time from image capture to result in milliseconds.
     *                   Useful for performance logging and UI feedback.
     */
    data class Recognised(
        val frame: Frame,
        val confidence: Float,
        val latencyMs: Long,
    ) : RecognitionResult() {
        init {
            require(confidence in 0f..1f) {
                "Confidence must be in [0.0, 1.0], got $confidence"
            }
        }
    }

    /**
     * No match found above the confidence threshold.
     * The scanner should continue searching.
     */
    object Unrecognised : RecognitionResult()

    /**
     * The recognition engine encountered a non-recoverable error.
     *
     * @param cause      Underlying exception (if available).
     * @param message    Human-readable description for logging.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : RecognitionResult()
}
