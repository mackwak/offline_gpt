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
    val addedAt: Long = System.currentTimeMillis()
)
