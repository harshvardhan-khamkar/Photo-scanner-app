package com.weddingmemory.app.domain.usecase

import com.weddingmemory.app.domain.exception.DomainException
import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.model.AlbumStatus
import com.weddingmemory.app.domain.repository.AlbumRepository
import com.weddingmemory.app.domain.repository.RecognitionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * InitializeAlbumUseCase — post-unlock setup pipeline for a wedding album.
 *
 * Orchestrates:
 *   1. Load the album from local store.
 *   2. Validate it is not expired and not already ready.
 *   3. Fetch the full frame manifest from the server (via repository).
 *   4. Persist frames locally.
 *   5. Load frame signatures into the recognition engine.
 *   6. Transition album status to [AlbumStatus.READY].
 *
 * Emits [InitializationState] checkpoints so the UI can display a
 * progress indicator during what may be a multi-second pipeline.
 *
 * This is intentionally a long-running use case — designed to run
 * inside a coroutine tied to a WorkManager task in production (Step 5+).
 */
class InitializeAlbumUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val recognitionRepository: RecognitionRepository,
) {
    operator fun invoke(albumId: String, nowMillis: Long = System.currentTimeMillis()): Flow<InitializationState> = flow {
        emit(InitializationState.LoadingAlbum)

        // Step 1 — Fetch album from local store
        val album = albumRepository.getAlbum(albumId)
            .getOrElse {
                emit(InitializationState.Failed(
                    DomainException.AlbumNotFound(albumId)
                ))
                return@flow
            }

        // Step 2 — Expiry check
        if (album.isExpired(nowMillis)) {
            albumRepository.updateAlbumStatus(albumId, AlbumStatus.EXPIRED)
            emit(InitializationState.Failed(DomainException.AlbumExpired(albumId)))
            return@flow
        }

        // Step 3 — Already initialised? Skip re-download.
        if (album.isFullyCached) {
            emit(InitializationState.LoadingSignatures)
            recognitionRepository.loadSignatures(albumId)
                .onFailure { emit(InitializationState.Failed(DomainException.RecognitionFailed(it))) }
                .onSuccess { emit(InitializationState.Ready(album)) }
            return@flow
        }

        // Step 4 — Persist the full album (data layer will download frames)
        emit(InitializationState.DownloadingFrames)
        albumRepository.updateAlbumStatus(albumId, AlbumStatus.INITIALIZING)
        albumRepository.saveAlbum(album)
            .onFailure {
                albumRepository.updateAlbumStatus(albumId, AlbumStatus.FAILED)
                emit(InitializationState.Failed(
                    DomainException.AlbumInitializationFailed(albumId, it)
                ))
                return@flow
            }

        // Step 5 — Load signatures into recognition engine
        emit(InitializationState.LoadingSignatures)
        recognitionRepository.loadSignatures(albumId)
            .onFailure {
                albumRepository.updateAlbumStatus(albumId, AlbumStatus.FAILED)
                emit(InitializationState.Failed(DomainException.RecognitionFailed(it)))
                return@flow
            }

        // Step 6 — Mark album READY
        albumRepository.updateAlbumStatus(albumId, AlbumStatus.READY)
        val readyAlbum = albumRepository.getAlbum(albumId)
            .getOrElse { album.copy(status = AlbumStatus.READY) }
        emit(InitializationState.Ready(readyAlbum))
    }
}

/** Checkpoints emitted during album initialisation. */
sealed class InitializationState {
    object LoadingAlbum : InitializationState()
    object DownloadingFrames : InitializationState()
    object LoadingSignatures : InitializationState()
    data class Ready(val album: Album) : InitializationState()
    data class Failed(val error: DomainException) : InitializationState()
}
