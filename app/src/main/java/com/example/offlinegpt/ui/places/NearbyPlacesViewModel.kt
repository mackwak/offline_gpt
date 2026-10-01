package com.example.offlinegpt.ui.places

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject

data class PlaceItem(
    val id: String,
    val name: String,
    val category: String,
    val rating: Double,
    val distance: String,
    val address: String,
    val isFavorite: Boolean = false
)

sealed class PlacesUiState {
    object Loading : PlacesUiState()
    data class Success(val places: List<PlaceItem>) : PlacesUiState()
    data class Error(val message: String) : PlacesUiState()
}

@HiltViewModel
class NearbyPlacesViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlacesUiState>(PlacesUiState.Loading)
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val favoritesSet = mutableSetOf<String>("1", "3", "6")
    private var allFetchedPlaces = listOf<PlaceItem>()

    init {
        fetchNearbyPlaces()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilter()
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
        applyFilter()
    }

    fun toggleFavorite(placeId: String) {
        if (favoritesSet.contains(placeId)) {
            favoritesSet.remove(placeId)
        } else {
            favoritesSet.add(placeId)
        }
        allFetchedPlaces = allFetchedPlaces.map { p ->
            if (p.id == placeId) p.copy(isFavorite = favoritesSet.contains(placeId)) else p
        }
        applyFilter()
    }

    fun fetchNearbyPlaces(apiKey: String? = null) {
        viewModelScope.launch {
            _uiState.value = PlacesUiState.Loading
            try {
                val userLocation = locationProvider.getCurrentLocation()
                val lat = userLocation?.latitude ?: 37.7749
                val lon = userLocation?.longitude ?: -122.4194

                withContext(Dispatchers.IO) {
                    val placesList = if (!apiKey.isNullOrBlank()) {
                        fetchFromGooglePlacesApi(lat, lon, apiKey, userLocation)
                    } else {
                        fetchFromNearbyPlacesApi(lat, lon, userLocation)
                    }

                    allFetchedPlaces = placesList
                    withContext(Dispatchers.Main) {
                        applyFilter()
                    }
                }
            } catch (_: Exception) {
                allFetchedPlaces = getFallbackPlaces()
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val query = _searchQuery.value
        val category = _selectedCategory.value

        val filtered = allFetchedPlaces.filter { place ->
            val matchesCategory = when (category) {
                "All" -> true
                "Favorites Only" -> place.isFavorite
                else -> place.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = query.isBlank() ||
                    place.name.contains(query, ignoreCase = true) ||
                    place.address.contains(query, ignoreCase = true) ||
                    place.category.contains(query, ignoreCase = true)

            matchesCategory && matchesQuery
        }

        _uiState.value = PlacesUiState.Success(filtered)
    }

    private fun fetchFromGooglePlacesApi(lat: Double, lon: Double, apiKey: String, userLocation: Location?): List<PlaceItem> {
        val urlString = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$lat,$lon&radius=1500&type=restaurant&key=$apiKey"
        val responseText = URL(urlString).readText()
        val json = JSONObject(responseText)
        val results = json.optJSONArray("results") ?: return getFallbackPlaces()

        val list = mutableListOf<PlaceItem>()
        for (i in 0 until results.length().coerceAtMost(10)) {
            val item = results.getJSONObject(i)
            val id = item.optString("place_id", i.toString())
            val name = item.optString("name", "Local Spot")
            val rating = item.optDouble("rating", 4.5)
            val address = item.optString("vicinity", "Nearby Address")
            val types = item.optJSONArray("types")
            val category = determineCategory(types)

            val placeLat = item.optJSONObject("geometry")?.optJSONObject("location")?.optDouble("lat", lat) ?: lat
            val placeLon = item.optJSONObject("geometry")?.optJSONObject("location")?.optDouble("lng", lon) ?: lon

            val distanceStr = calculateDistanceString(userLocation, placeLat, placeLon)

            list.add(
                PlaceItem(
                    id = id,
                    name = name,
                    category = category,
                    rating = rating,
                    distance = distanceStr,
                    address = address,
                    isFavorite = favoritesSet.contains(id)
                )
            )
        }
        return if (list.isNotEmpty()) list else getFallbackPlaces()
    }

    private fun fetchFromNearbyPlacesApi(lat: Double, lon: Double, userLocation: Location?): List<PlaceItem> {
        return try {
            val query = "[out:json];node(around:2000,$lat,$lon)[amenity~\"restaurant|cafe|bakery|fast_food|park\"];out 12;"
            val urlString = "https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(query, "UTF-8")
            val responseText = URL(urlString).readText()
            val json = JSONObject(responseText)
            val elements = json.optJSONArray("elements") ?: return getFallbackPlaces()

            val list = mutableListOf<PlaceItem>()
            for (i in 0 until elements.length().coerceAtMost(10)) {
                val item = elements.getJSONObject(i)
                val id = item.optLong("id", i.toLong()).toString()
                val tags = item.optJSONObject("tags")
                val name = tags?.optString("name")?.takeIf { it.isNotBlank() } ?: continue
                val amenity = tags.optString("amenity", "restaurant")
                val category = when (amenity) {
                    "cafe" -> "Cafe"
                    "bakery" -> "Bakery"
                    "park" -> "Park"
                    else -> "Restaurant"
                }

                val pLat = item.optDouble("lat", lat)
                val pLon = item.optDouble("lon", lon)
                val distanceStr = calculateDistanceString(userLocation, pLat, pLon)

                list.add(
                    PlaceItem(
                        id = id,
                        name = name,
                        category = category,
                        rating = 4.2 + (i % 8) * 0.1,
                        distance = distanceStr,
                        address = tags.optString("addr:street", "Local Area"),
                        isFavorite = favoritesSet.contains(id)
                    )
                )
            }
            if (list.isNotEmpty()) list else getFallbackPlaces()
        } catch (_: Exception) {
            getFallbackPlaces()
        }
    }

    private fun calculateDistanceString(userLoc: Location?, targetLat: Double, targetLon: Double): String {
        if (userLoc == null) return "350m"
        val results = FloatArray(1)
        Location.distanceBetween(userLoc.latitude, userLoc.longitude, targetLat, targetLon, results)
        val distMeters = results[0].toInt()
        return if (distMeters >= 1000) {
            "%.1fkm".format(distMeters / 1000.0)
        } else {
            "${distMeters}m"
        }
    }

    private fun determineCategory(typesJson: org.json.JSONArray?): String {
        if (typesJson == null) return "Restaurant"
        for (i in 0 until typesJson.length()) {
            val type = typesJson.optString(i)
            when {
                type.contains("cafe", ignoreCase = true) -> return "Cafe"
                type.contains("bakery", ignoreCase = true) -> return "Bakery"
                type.contains("park", ignoreCase = true) -> return "Park"
                type.contains("restaurant", ignoreCase = true) -> return "Restaurant"
            }
        }
        return "Restaurant"
    }

    private fun getFallbackPlaces(): List<PlaceItem> {
        return listOf(/*
            PlaceItem("1", "Blue Bottle Coffee", "Cafe", 4.7, "250m", "123 Main St", favoritesSet.contains("1")),
            PlaceItem("2", "Gourmet Burger Kitchen", "Restaurant", 4.5, "450m", "45 Park Ave", favoritesSet.contains("2")),
            PlaceItem("3", "Artisan Bakery & Pastry", "Bakery", 4.8, "600m", "78 Baker St", favoritesSet.contains("3")),
            PlaceItem("4", "Central Community Park", "Park", 4.6, "800m", "200 Green Way", favoritesSet.contains("4")),
            PlaceItem("5", "Espresso Express Cafe", "Cafe", 4.3, "300m", "15 High St", favoritesSet.contains("5")),
            PlaceItem("6", "Tokyo Ramen House", "Restaurant", 4.9, "950m", "89 Noodle Lane", favoritesSet.contains("6"))
       */ )
    }
}
