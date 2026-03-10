package com.weddingmemory.app.domain.model

/**
 * PlaybackSegment — everything a video player needs to play one memory.
 *
 * Returned by [com.weddingmemory.app.domain.usecase.GetPlaybackSegmentUseCase]
 * after a successful [RecognitionResult.Recognised] event.
 *
 * The domain deliberately does not import ExoPlayer.
 * The UI layer maps this to an ExoPlayer MediaItem.
 *
 * @param frameId       The [Frame.id] that triggered this playback.
 * @param albumId       Parent [Album.id] — needed for logging and analytics.
 * @param videoUrl      Fully-qualified URL ready to pass to the media player.
 * @param durationMs    Known duration of the clip in milliseconds.
 *                      0L means unknown — the player will derive it from the stream.
 * @param mimeType      Optional MIME type hint (e.g. "application/x-mpegURL" for HLS,
 *                      "video/mp4" for progressive). Null = let the player auto-detect.
 * @param startOffsetMs Optional start offset within the stream (for clipped segments).
 *                      0L = play from beginning.
 */
data class PlaybackSegment(
    val frameId: String,
    val albumId: String,
    val videoUrl: String,
    val durationMs: Long,
    val mimeType: String? = null,
    val startOffsetMs: Long = 0L,
) {
    /** Derives whether this is an HLS stream from the MIME type or URL extension. */
    val isHls: Boolean
        get() = mimeType == "application/x-mpegURL"
            || videoUrl.contains(".m3u8", ignoreCase = true)
}
