package com.example.offlinegpt.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "rag_contexts")
data class ContextEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as ContextEntity
        return id == other.id && text == other.text && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
