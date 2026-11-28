package com.example.offlinegpt.di

import android.content.Context
import android.os.Environment
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
object EmbeddingModule {

    @Provides
    @Singleton
    fun provideTextEmbedder(
        @ApplicationContext context: Context
    ): TextEmbedder? {
        val fileName = "universal-sentence-encoder.tflite"
        val modelFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (!modelFile.exists()) {
            return null
        }

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelFile.absolutePath)
                .build()

            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()

            TextEmbedder.createFromOptions(context, options)
        } catch (_: Exception) {
            null
        }
    }
}