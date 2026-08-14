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
    ): TextEmbedder {

        val fileName = "universal-sentence-encoder.tflite"
        val modelFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        val baseOptionsBuilder = BaseOptions.builder()
        if (modelFile.exists()) {
            baseOptionsBuilder.setModelAssetPath(modelFile.absolutePath)
        } else {
            // Fallback to a model in the assets folder if the downloaded one isn't ready.
            // Note: MediaPipe Android requires a slash in the asset path to avoid a native crash.
            baseOptionsBuilder.setModelAssetPath("./universal_sentence_encoder.tflite")
        }

        val baseOptions = baseOptionsBuilder.build()

        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()

        return TextEmbedder.createFromOptions(context, options)
    }
}