package com.weddingmemory.app.domain.usecase

import com.weddingmemory.app.domain.exception.DomainException
import com.weddingmemory.app.domain.model.PlaybackSegment
import com.weddingmemory.app.domain.repository.AlbumRepository
import javax.inject.Inject

/**
 * GetPlaybackSegmentUseCase — resolves a [PlaybackSegment] from a
 * matched [com.weddingmemory.app.domain.model.Frame].
 *
 * Called immediately after [RecognizeFrameUseCase] emits a
 * [com.weddingmemory.app.domain.model.RecognitionResult.Recognised] event.
 * Provides all information the video player needs in one clean object
 * without the ViewModel needing to inspect Frame internals.
 *
 * Design decision — suspend + Result (not Flow):
 *   This is a single-shot, synchronous-ish read from the local store.
 *   There is no streaming, no polling. [Flow] would add unnecessary
 *   complexity without benefit.
 */
class GetPlaybackSegmentUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
) {
    /**
     * @param albumId The parent album identifier.
     * @param frameId The matched frame's identifier.
     * @return [Result.success] with the [PlaybackSegment], or
     *         [Result.failure] with a [DomainException] if the frame
     *         cannot be found in the local store.
     */
    suspend operator fun invoke(albumId: String, frameId: String): Result<PlaybackSegment> {
        val frames = albumRepository.getFrames(albumId)
            .getOrElse { return Result.failure(DomainException.AlbumNotFound(albumId)) }

        val frame = frames.find { it.id == frameId }
            ?: return Result.failure(
                DomainException.AlbumNotFound(
                    albumId = albumId,
                )
            )

        return Result.success(
            PlaybackSegment(
                frameId = frame.id,
                albumId = albumId,
                videoUrl = frame.videoUrl,
                durationMs = frame.durationMs,
                // MIME type is opaque at the domain level — the data layer
                // or Frame.metadata may supply it in a later step.
                mimeType = frame.metadata["mime_type"],
                startOffsetMs = frame.metadata["start_offset_ms"]?.toLongOrNull() ?: 0L,
            )
        )
    }
}
