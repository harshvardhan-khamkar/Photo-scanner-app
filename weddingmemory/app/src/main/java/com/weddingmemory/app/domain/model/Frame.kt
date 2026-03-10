package com.weddingmemory.app.domain.model

/**
 * Frame — a single recognisable unit within an [Album].
 *
 * A "frame" represents one physical wedding photograph that guests can
 * hold up to the camera. When recognised, it triggers playback of [videoUrl].
 *
 * @param id            Unique identifier within the album (server-assigned).
 * @param albumId       Parent album identifier.
 * @param index         Display order within the album (0-based).
 * @param imageSignature Opaque feature descriptor used by the recognition engine.
 *                       This can be a perceptual hash, an embedding vector encoded
 *                       as Base64, or any binary representation — the domain does
 *                       not prescribe the format.
 * @param videoUrl      Absolute URL of the video segment to play on recognition.
 * @param thumbnailUrl  URL of the preview thumbnail shown in album galleries.
 * @param durationMs    Duration of the associated video in milliseconds.
 * @param metadata      Arbitrary key-value extensions for future features
 *                      (e.g. "couple_names", "song_title") without model churn.
 */
data class Frame(
    val id: String,
    val albumId: String,
    val index: Int,
    val imageSignature: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val startTimeMs: Long = 0,
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Returns true when this frame has a non-blank signature and a reachable video URL.
     * Used as a lightweight sanity check before caching or recognition.
     */
    val isComplete: Boolean
        get() = imageSignature.isNotBlank() && videoUrl.isNotBlank()
}
