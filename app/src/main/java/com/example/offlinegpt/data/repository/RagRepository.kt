package com.example.offlinegpt.data.repository

import android.util.Log
import com.example.offlinegpt.data.local.ContextDao
import com.example.offlinegpt.data.local.ContextEntity
import com.example.offlinegpt.data.local.cosineSimilarity
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagRepository @Inject constructor(
    private val dao: ContextDao,
    private val textEmbedderProvider: dagger.Lazy<TextEmbedder>
) {
    private val textEmbedder get() = textEmbedderProvider.get()

    suspend fun ingestContexts(stringArray: Array<String>) {
        val entities = stringArray.map { rawText ->
            val result = textEmbedder.embed(rawText)
            val floatVector = result.embeddingResult().embeddings().first().floatEmbedding()
            ContextEntity(text = rawText, embedding = floatVector)
        }
        dao.insertAll(entities)
    }

    suspend fun searchSimilarContexts(userQuery: String, topK: Int = 3): List<String> {
        val queryResult = textEmbedder.embed(userQuery)
        val queryVector = queryResult.embeddingResult().embeddings().first().floatEmbedding()

        Log.d("RagRepository", "getAllContexts size: ${dao.getAllContexts().size}}")

        return dao.getAllContexts()
            .map { record -> record.text to queryVector.cosineSimilarity(record.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    suspend fun clear() {
        return dao.removeAll()
    }
}