package com.weddingmemory.app.domain.usecase

import com.weddingmemory.app.domain.exception.DomainException
import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UnlockAlbumUseCase — verifies a guest's album code with the server
 * and returns the unlocked [Album].
 *
 * Emits [UnlockAlbumState] as a [Flow] so the UI can show a loading
 * spinner, handle success, and surface typed errors without a try/catch.
 *
 * Input validation (blank code) is enforced here, not in the ViewModel,
 * so the rule is testable without any Android dependency.
 */
class UnlockAlbumUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
) {
    operator fun invoke(code: String): Flow<UnlockAlbumState> = flow {
        emit(UnlockAlbumState.Loading)

        val trimmedCode = code.trim().uppercase()
        if (trimmedCode.isBlank()) {
            emit(UnlockAlbumState.InvalidCode("Album code cannot be blank."))
            return@flow
        }

        albumRepository.unlockAlbum(trimmedCode)
            .onSuccess { album ->
                emit(UnlockAlbumState.Success(album))
            }
            .onFailure { throwable ->
                val state = when (throwable) {
                    is DomainException.InvalidAlbumCode ->
                        UnlockAlbumState.InvalidCode(throwable.message)
                    is DomainException.AlbumExpired ->
                        UnlockAlbumState.Expired(throwable.albumId)
                    is DomainException.NetworkUnavailable ->
                        UnlockAlbumState.Offline
                    is DomainException.ServerError ->
                        UnlockAlbumState.Error("Server error (${throwable.httpCode}). Try again.")
                    else ->
                        UnlockAlbumState.Error(throwable.message ?: "Unexpected error.")
                }
                emit(state)
            }
    }
}

/** UI-facing state emitted by [UnlockAlbumUseCase]. */
sealed class UnlockAlbumState {
    object Loading : UnlockAlbumState()
    data class Success(val album: Album) : UnlockAlbumState()
    data class InvalidCode(val reason: String) : UnlockAlbumState()
    data class Expired(val albumId: String) : UnlockAlbumState()
    object Offline : UnlockAlbumState()
    data class Error(val message: String) : UnlockAlbumState()
}
