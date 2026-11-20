package com.example.offlinegpt.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey val placeId: String,
    val name: String,
    val category: String,
    val rating: Double,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val currentOpeningHours: String? = null,
    val websiteUri: String? = null,
    val editorialSummary: String? = null,
    val generativeSummary: String? = null,
    val embedding: FloatArray,
    val addedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FavoritePlaceEntity
        if (placeId != other.placeId) return false
        if (name != other.name) return false
        if (category != other.category) return false
        if (rating != other.rating) return false
        if (address != other.address) return false
        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (currentOpeningHours != other.currentOpeningHours) return false
        if (websiteUri != other.websiteUri) return false
        if (editorialSummary != other.editorialSummary) return false
        if (generativeSummary != other.generativeSummary) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (addedAt != other.addedAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = placeId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + rating.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + (currentOpeningHours?.hashCode() ?: 0)
        result = 31 * result + (websiteUri?.hashCode() ?: 0)
        result = 31 * result + (editorialSummary?.hashCode() ?: 0)
        result = 31 * result + (generativeSummary?.hashCode() ?: 0)
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + addedAt.hashCode()
        return result
    }
}
