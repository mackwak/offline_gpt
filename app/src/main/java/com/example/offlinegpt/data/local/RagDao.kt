package com.example.offlinegpt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<DocumentPage>)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPagesForDocument(documentId: Long): List<DocumentPage>

    @Query("SELECT * FROM document_pages")
    suspend fun getAllPages(): List<DocumentPage>

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocument(documentId: Long)
}
