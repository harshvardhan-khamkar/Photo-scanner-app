package com.weddingmemory.app.ui.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weddingmemory.app.core.dispatcher.DispatcherProvider
import com.weddingmemory.app.domain.model.Frame
import com.weddingmemory.app.domain.model.RecognitionResult
import com.weddingmemory.app.domain.usecase.RecognizeFrameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * ScannerViewModel — owns the CameraX lifecycle and recognition pipeline.
 *
 * State machine:
 *   Idle → Ready (camera bound)
 *        → Recognizing (frame submitted)
 *          → Matched(frame)   — Fragment navigates to Player
 *          → NoMatch          — Fragment resets to Ready, scanner continues
 *          → Error(message)   — Fragment shows toast / snackbar
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val recognizeFrameUseCase: RecognizeFrameUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Dedicated single-thread executor for ImageAnalysis callbacks (not for coroutines). */
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /**
     * Atomic flag — true while a recognition coroutine is in flight.
     * Written and read from different threads; AtomicBoolean avoids data races
     * that would occur if we relied on reading [_uiState] from the analysis thread.
     */
    private val isRecognizing = AtomicBoolean(false)

    /**
     * Epoch-millis of the last frame that passed the throttle gate.
     * @Volatile ensures the analysisExecutor thread always reads the latest value.
     */
    @Volatile
    private var lastAnalysisTimestamp = 0L

    /** Minimum time between processed frames in milliseconds. */
    private val frameIntervalMs = 500L

    /** Album currently being scanned — set by ScannerFragment from nav args. */
    private var currentAlbumId = ""

    fun setAlbumId(albumId: String) {
        currentAlbumId = albumId
        Timber.d("ScannerViewModel: albumId set to $albumId")
    }

    // -------------------------------------------------------------------------
    // Camera setup
    // -------------------------------------------------------------------------

    fun startCamera(
        cameraProvider: ProcessCameraProvider,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
    ) {
        viewModelScope.launch(dispatchers.main) {
            try {
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, ::analyzeImage) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )

                _uiState.value = ScannerUiState.Ready
                Timber.d("Camera bound — preview + analysis running")
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind camera")
                _uiState.value = ScannerUiState.Error(e.message ?: "Camera error")
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.value = ScannerUiState.PermissionDenied
    }

    /** Called by the Fragment after navigating away from Matched state. */
    fun resetToReady() {
        if (_uiState.value is ScannerUiState.Matched) {
            _uiState.value = ScannerUiState.Ready
        }
    }

    // -------------------------------------------------------------------------
    // Image analysis
    // -------------------------------------------------------------------------

    private fun analyzeImage(proxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // Gate 1 — 500 ms throttle: drop frames that arrive too soon.
        // Checked first because it is the cheapest operation.
        if (now - lastAnalysisTimestamp < frameIntervalMs) {
            proxy.close()
            return
        }

        // Gate 2 — Concurrency guard: drop frame if a recognition job is already running.
        // compareAndSet atomically flips false → true; returns false if already true.
        if (!isRecognizing.compareAndSet(false, true)) {
            proxy.close()
            return
        }

        lastAnalysisTimestamp = now
        val bitmap = proxy.toBitmapSafe()
        proxy.close() // always close before launching coroutine — never block the executor

        if (bitmap == null) return

        // Gate 3 — Analysis runs on Dispatchers.Default, never on the UI thread
        // or the single-thread analysisExecutor.
        viewModelScope.launch(dispatchers.default) {
            try {
                recognizeFrameUseCase(currentAlbumId, bitmap).collect { result ->
                    handleResult(result)
                }
            } finally {
                // Always release the lock — even if the use case throws.
                isRecognizing.set(false)
            }
        }
    }

    private fun handleResult(result: RecognitionResult) {
        _uiState.value = when (result) {
            is RecognitionResult.Scanning      -> ScannerUiState.Recognizing
            is RecognitionResult.Recognised    -> ScannerUiState.Matched(result.frame)
            is RecognitionResult.Unrecognised  -> ScannerUiState.NoMatch
            is RecognitionResult.Error         -> ScannerUiState.Error(result.message)
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
    }
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

// Imports moved to top of file

private fun ImageProxy.toBitmapSafe(): Bitmap? {
    val rawBitmap = if (format != ImageFormat.YUV_420_888) {
        // Fallback for emulators/weird devices that might already provide RGBA
        try { this.toBitmap() } catch (e: Exception) { null }
    } else {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()

        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } ?: return null

    return rawBitmap.rotateIfNeeded(imageInfo.rotationDegrees)
}

private fun Bitmap.rotateIfNeeded(rotationDegrees: Int): Bitmap {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    if (normalizedRotation == 0) return this

    val matrix = Matrix().apply {
        postRotate(normalizedRotation.toFloat())
    }

    return try {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
            if (rotated !== this) recycle()
        }
    } catch (e: Exception) {
        this
    }
}

// -------------------------------------------------------------------------
// UI State
// -------------------------------------------------------------------------

sealed class ScannerUiState {
    /** Camera not started yet. */
    object Idle : ScannerUiState()

    /** Camera is live — ready to analyse frames. */
    object Ready : ScannerUiState()

    /** Recognition engine is processing a frame — show overlay. */
    object Recognizing : ScannerUiState()

    /** A frame was matched — navigate to Player. */
    data class Matched(val frame: Frame) : ScannerUiState()

    /** No match — scanner continues automatically. */
    object NoMatch : ScannerUiState()

    /** Unrecoverable error — show message. */
    data class Error(val message: String) : ScannerUiState()

    /** Camera permission denied — show permission-required layout. */
    object PermissionDenied : ScannerUiState()
}
