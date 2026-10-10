package com.example.offlinegpt.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
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

    suspend fun ingestPdf(uri: Uri, embedder: (String) -> FloatArray) = withContext(Dispatchers.IO) {

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream)
                val totalPages = document.numberOfPages
                Log.d("ChatViewModel", "PDF loaded. Total pages: $totalPages")

                val fileName = getFileName(uri)
                val file = copyUriToFile(uri, fileName)
                val documentId = ragDao.insertDocument(Document(fileName = fileName, filePath = file.absolutePath))

                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()

                // Extract text page by page and save to database immediately
                for (pageIndex in 1..totalPages) {
                    stripper.startPage = pageIndex
                    stripper.endPage = pageIndex
                    val pageText = stripper.getText(document).trim()

                    if (pageText.isNotEmpty()) {
                        // Further split page text by paragraphs if it's very long
                        val paragraphs = pageText.split(Regex("\\n\\s*\\n"))
                            .map { it.trim() }
                            .filter { it.length > 20 }

                        if (paragraphs.isNotEmpty()) {
                            val page = paragraphs.joinToString(separator = "\n\n")
                            val documentPage = DocumentPage(
                                documentId = documentId,
                                pageNumber = pageIndex,
                                content = page,
                                embedding = embedder(page)
                            )
                            ragDao.insertPage(documentPage)
                            Log.d("ChatViewModel", "Page $pageIndex ingested with ${paragraphs.size} chunks")
                        }
                    }
                }
                document.close()
                Log.d("ChatViewModel", "PDF ingestion completed for $fileName")
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error ingesting PDF", e)
        }
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

    suspend fun findRelevantPages(queryEmbedding: FloatArray, topK: Int = 3): List<String> {
        val allPages = ragDao.getAllPages()

        Log.d("RagRepository", "All Pages: $allPages")

        return allPages.map { page ->


            Log.d("RagRepository", "All Page: ${page.content}")

            val score = cosineSimilarity(queryEmbedding, page.embedding)
            page to score
        }.sortedByDescending { it.second }
            .take(topK)
            .map { it.first.content }
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

    suspend fun getAllPages() {
        val allPages = ragDao.getAllPages()
        allPages.forEach { page ->
            Log.d("RagRepository", "Page: ${page.content}")
        }
    }
 
    suspend fun removeAllPages() {
        ragDao.deleteAllDocuments()
        ragDao.removeAllPages()
    }
}
