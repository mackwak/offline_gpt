package com.example.offlinegpt.data.engine

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var textEmbedder: TextEmbedder? = null

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelPath)
            .build()
        
        val options = TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()
            
        textEmbedder = TextEmbedder.createFromOptions(context, options)
    }

    fun isInitialized(): Boolean = textEmbedder != null

    fun embed(text: String): FloatArray {
        val embedder = textEmbedder ?: throw IllegalStateException("EmbeddingEngine not initialized")
        val result = embedder.embed(text)
        return result.embeddingResult().embeddings().first().floatEmbedding()
    }
}
