package com.weddingmemory.app.ui.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weddingmemory.app.core.dispatcher.DispatcherProvider
import com.weddingmemory.app.domain.usecase.InitializeAlbumUseCase
import com.weddingmemory.app.domain.usecase.InitializationState
import com.weddingmemory.app.domain.usecase.UnlockAlbumState
import com.weddingmemory.app.domain.usecase.UnlockAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlbumUnlockViewModel — manages album code entry, unlock, and initialization.
 *
 * State machine:
 *   Idle
 *     → Loading          (unlock network call in flight)
 *     → Initializing     (downloading frames / loading signatures)
 *     → Success          (fully initialized — Fragment navigates to Scanner)
 *     → Error(message)   (unlock or init failure — Fragment stays on screen)
 */
@HiltViewModel
class AlbumUnlockViewModel @Inject constructor(
    private val unlockAlbumUseCase: UnlockAlbumUseCase,
    private val initializeAlbumUseCase: InitializeAlbumUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UnlockUiState>(UnlockUiState.Idle)
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun onUnlockClicked(code: String) {
        // Prevent duplicate taps while a request is in flight
        val current = _uiState.value
        if (current is UnlockUiState.Loading || current is UnlockUiState.Initializing) return

        viewModelScope.launch(dispatchers.io) {
            // Phase 1 — Unlock
            var albumId: String? = null

            unlockAlbumUseCase(code).collect { state ->
                when (state) {
                    is UnlockAlbumState.Loading ->
                        _uiState.value = UnlockUiState.Loading

                    is UnlockAlbumState.Success -> {
                        // Capture albumId; Phase 2 begins after collect finishes
                        albumId = state.album.id
                    }

                    is UnlockAlbumState.InvalidCode ->
                        _uiState.value = UnlockUiState.Error(state.reason)

                    is UnlockAlbumState.Expired ->
                        _uiState.value = UnlockUiState.Error("This album has expired.")

                    is UnlockAlbumState.Offline ->
                        _uiState.value = UnlockUiState.Error("No internet connection.")

                    is UnlockAlbumState.Error ->
                        _uiState.value = UnlockUiState.Error(state.message)
                }
            }

            // Phase 2 — Initialize (only reached if unlock succeeded)
            val id = albumId ?: return@launch

            initializeAlbumUseCase(id).collect { initState ->
                _uiState.value = when (initState) {
                    is InitializationState.LoadingAlbum ->
                        UnlockUiState.Initializing("Loading album…")

                    is InitializationState.DownloadingFrames ->
                        UnlockUiState.Initializing("Downloading frames…")

                    is InitializationState.LoadingSignatures ->
                        UnlockUiState.Initializing("Preparing recognition engine…")

                    is InitializationState.Ready ->
                        UnlockUiState.Success(albumId = id)

                    is InitializationState.Failed ->
                        UnlockUiState.Error(
                            initState.error.message ?: "Initialization failed. Try again."
                        )
                }
            }
        }
    }

    /** Called after the Fragment has consumed a terminal state (Success / Error). */
    fun resetState() {
        _uiState.value = UnlockUiState.Idle
    }
}

// -------------------------------------------------------------------------
// UI State
// -------------------------------------------------------------------------

sealed class UnlockUiState {
    /** Initial state — nothing has happened yet. */
    object Idle : UnlockUiState()

    /** Unlock request in flight — show progress, disable button. */
    object Loading : UnlockUiState()

    /**
     * Album found — now downloading frames and loading recognition engine.
     * [message] describes the current initialization step for the progress label.
     */
    data class Initializing(val message: String) : UnlockUiState()

    /** Fully initialized — Fragment navigates to Scanner. Carries [albumId] for nav args. */
    data class Success(val albumId: String) : UnlockUiState()

    /** Unlock or initialization failed — show [message], stay on screen. */
    data class Error(val message: String) : UnlockUiState()
}
