package com.weddingmemory.app.domain.usecase

import com.weddingmemory.app.domain.exception.DomainException
import com.weddingmemory.app.domain.model.RecognitionResult
import com.weddingmemory.app.domain.repository.RecognitionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

import android.graphics.Bitmap

/**
 * RecognizeFrameUseCase — runs one captured image through the recognition
 * engine and emits the result.
 *
 * This use case acts as a guard:
 *  - Ensures the engine is ready before dispatching to the repository.
 *  - Emits [RecognitionResult.Error] with a typed [DomainException]
 *    instead of throwing, keeping the Flow cold and the VM clean.
 *
 * Performance note: this use case is called on every camera frame (up to 30fps).
 * The repository implementation is responsible for debouncing / throttling.
 * The domain layer does not mandate a frame rate policy.
 */
class RecognizeFrameUseCase @Inject constructor(
    private val recognitionRepository: RecognitionRepository,
) {
    /**
     * @param albumId   The album to search against.
     * @param image     Decoded Bitmap from the camera.
     */
    operator fun invoke(albumId: String, image: Bitmap): Flow<RecognitionResult> = flow {
        // If engine is not ready (e.g. model file missing), degrade gracefully —
        // return Unrecognised instead of Error so the scanner keeps running smoothly.
        if (!recognitionRepository.isEngineReady(albumId)) {
            emit(RecognitionResult.Unrecognised)
            return@flow
        }

        recognitionRepository.recognizeFrame(albumId, image)
            .collect { result -> emit(result) }
    }
}
