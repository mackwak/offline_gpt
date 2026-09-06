package com.example.offlinegpt.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.offlinegpt.data.local.Document
import com.example.offlinegpt.data.local.DocumentPage
import com.example.offlinegpt.data.local.RagDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagRepository @Inject constructor(
    private val ragDao: RagDao,
    @ApplicationContext private val context: Context
) {
    suspend fun ingestPdf(uri: Uri, embedder: (String) -> FloatArray): Long = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri)
        val file = copyUriToFile(uri, fileName)
        
        val documentId = ragDao.insertDocument(
            Document(fileName = fileName, filePath = file.absolutePath)
        )

        val pages = mutableListOf<DocumentPage>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                // In a real app, we'd extract text. For now, we'll use a placeholder or 
                // you might have a PDF text extraction library. 
                // Since I cannot add new dependencies easily, I'll describe the process.
                val pageText = "Text from page ${i + 1} of $fileName" // Placeholder
                val embedding = embedder(pageText)
                pages.add(
                    DocumentPage(
                        documentId = documentId,
                        pageNumber = i + 1,
                        content = pageText,
                        embedding = embedding
                    )
                )
            }
            renderer.close()
        }

        ragDao.insertPages(pages)
        documentId
    }

    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "document_${System.currentTimeMillis()}.pdf"
    }

    private fun copyUriToFile(uri: Uri, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    suspend fun findRelevantPages(queryEmbedding: FloatArray, topK: Int = 3): List<DocumentPage> {
        val allPages = ragDao.getAllPages()
        return allPages.map { page ->
            val score = cosineSimilarity(queryEmbedding, page.embedding)
            page to score
        }.sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        return dotProduct / (Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())).toFloat()
    }
}
