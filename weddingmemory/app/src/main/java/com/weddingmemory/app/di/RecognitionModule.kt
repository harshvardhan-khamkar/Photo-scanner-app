package com.weddingmemory.app.di

import com.weddingmemory.app.data.repository.RecognitionRepositoryImpl
import com.weddingmemory.app.domain.repository.RecognitionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * RecognitionModule — binds [RecognitionRepository] to [RecognitionRepositoryImpl].
 *
 * Uses TFLite (MobileNetV2 feature extractor) for real embedding extraction
 * and cosine similarity matching.
 *
 * To revert to fake for demos (no model file required):
 *   swap impl → fake: FakeRecognitionRepository
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecognitionModule {

    @Binds
    @Singleton
    abstract fun bindRecognitionRepository(
        impl: RecognitionRepositoryImpl
    ): RecognitionRepository
}

