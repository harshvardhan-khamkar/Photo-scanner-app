package com.weddingmemory.app.ui.player

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? = null
        private set

    private var preparedVideoUrl: String? = null
    private var preparedStartMs: Long = -1L
    private var preparedDurationMs: Long = -1L

    // Position monitor to stop at end timestamp
    private val handler = Handler(Looper.getMainLooper())
    private var endPositionMs: Long = Long.MAX_VALUE
    private val positionChecker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.currentPosition >= endPositionMs) {
                Timber.d("ExoPlayer: reached end %dms, stopping", endPositionMs)
                p.pause()
                _uiState.value = PlayerUiState.Ended
                return
            }
            handler.postDelayed(this, 200L)
        }
    }

    fun prepareVideo(videoUrl: String, startTimestampMs: Long, clipDurationMs: Long) {
        if (videoUrl.isBlank()) {
            _uiState.value = PlayerUiState.Error("Missing video URL.")
            return
        }

        val normalizedStartMs = startTimestampMs.coerceAtLeast(0L)
        val normalizedDurationMs = clipDurationMs.coerceAtLeast(0L)

        val isSameRequest = player != null
            && preparedVideoUrl == videoUrl
            && preparedStartMs == normalizedStartMs
            && preparedDurationMs == normalizedDurationMs

        if (isSameRequest) return

        handler.removeCallbacks(positionChecker)
        player?.release()
        player = null

        preparedVideoUrl = videoUrl
        preparedStartMs = normalizedStartMs
        preparedDurationMs = normalizedDurationMs

        endPositionMs = if (normalizedDurationMs > 0L) {
            normalizedStartMs + normalizedDurationMs
        } else {
            Long.MAX_VALUE
        }

        _uiState.value = PlayerUiState.Buffering

        val exoPlayer = ExoPlayer.Builder(getApplication<Application>().applicationContext)
            .build()
            .also { player = it }

        var hasStartedMonitor = false

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _uiState.value = when (state) {
                    Player.STATE_BUFFERING -> PlayerUiState.Buffering
                    Player.STATE_READY -> PlayerUiState.Playing
                    Player.STATE_ENDED -> PlayerUiState.Ended
                    else -> _uiState.value
                }

                // Start end-position monitor once ready
                if (state == Player.STATE_READY && !hasStartedMonitor) {
                    hasStartedMonitor = true
                    if (endPositionMs < Long.MAX_VALUE) {
                        handler.post(positionChecker)
                        Timber.d("ExoPlayer: monitor started, will stop at %dms", endPositionMs)
                    }
                }

                Timber.d("ExoPlayer state=%d position=%dms", state, exoPlayer.currentPosition)
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.e(error, "ExoPlayer error")
                _uiState.value = PlayerUiState.Error(
                    error.message ?: "Video playback failed."
                )
            }
        })

        Timber.d(
            "Preparing video start=%dms end=%dms duration=%dms url=%s",
            normalizedStartMs,
            if (endPositionMs == Long.MAX_VALUE) -1L else endPositionMs,
            normalizedDurationMs,
            videoUrl
        )

        // NO ClippingMediaSource — just plain MediaItem + seekTo before prepare
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        if (normalizedStartMs > 0) {
            exoPlayer.seekTo(normalizedStartMs)
            Timber.d("ExoPlayer: seekTo(%dms) before prepare", normalizedStartMs)
        }
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(positionChecker)
        player?.release()
        player = null
        preparedVideoUrl = null
        preparedStartMs = -1L
        preparedDurationMs = -1L
        Timber.d("PlayerViewModel: ExoPlayer released")
    }
}

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    object Buffering : PlayerUiState()
    object Playing : PlayerUiState()
    object Ended : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}
