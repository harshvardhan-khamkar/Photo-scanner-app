package com.weddingmemory.app.data.repository.fake

import android.graphics.Bitmap
import com.weddingmemory.app.domain.model.Frame
import com.weddingmemory.app.domain.model.RecognitionResult
import com.weddingmemory.app.domain.repository.RecognitionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random
import javax.inject.Inject

/**
 * FakeRecognitionRepository — stub used until ML Kit / TFLite is integrated.
 *
 * Behaviour per [recognizeFrame] call:
 *  1. Emits [RecognitionResult.Scanning] immediately.
 *  2. Waits 400 ms to simulate engine processing time.
 *  3. Randomly emits [RecognitionResult.Recognised] (~50%) or
 *     [RecognitionResult.Unrecognised] (~50%).
 *
 * [isEngineReady] always returns true so [RecognizeFrameUseCase] does
 * not gate the call — no real engine to load.
 *
 * Replace with [RecognitionRepositoryImpl] (ML Kit adapter) in a later step.
 */
class FakeRecognitionRepository @Inject constructor() : RecognitionRepository {

    override suspend fun loadSignatures(albumId: String): Result<Unit> = Result.success(Unit)

    override suspend fun releaseSignatures(albumId: String) = Unit

    override fun isEngineReady(albumId: String): Boolean = true

    override fun recognizeFrame(
        albumId: String,
        image: Bitmap,
    ): Flow<RecognitionResult> = flow {
        emit(RecognitionResult.Scanning)
        delay(400L)

        if (Random.nextBoolean()) {
            emit(
                RecognitionResult.Recognised(
                    frame = Frame(
                        id = "fake-frame-001",
                        albumId = albumId,
                        index = 0,
                        imageSignature = "",
                        videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        thumbnailUrl = "",
                        durationMs = 30_000L,
                    ),
                    confidence = 0.95f,
                    latencyMs = 400L,
                )
            )
        } else {
            emit(RecognitionResult.Unrecognised)
        }
    }
}
