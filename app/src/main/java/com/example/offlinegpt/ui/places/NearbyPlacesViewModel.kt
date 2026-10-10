package com.example.offlinegpt.ui.places

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinegpt.data.local.FavoritePlaceDao
import com.example.offlinegpt.data.local.FavoritePlaceEntity
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.inject.Inject

data class CandidateAddress(
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double
)

data class PlaceItem(
    val id: String,
    val name: String,
    val category: String,
    val rating: Double,
    val distance: String,
    val address: String,
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
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
    private val favoritePlaceDao: FavoritePlaceDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private companion object {
        const val DEFAULT_PLACES_API_KEY = "AIzaSyAQmeB6_St_i76_4zpLBKOP5EWaWIl2hnY"
    }

    private val _uiState = MutableStateFlow<PlacesUiState>(PlacesUiState.Loading)
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    private val _locationQuery = MutableStateFlow("")
    val locationQuery: StateFlow<String> = _locationQuery.asStateFlow()

    private val _addressCandidates = MutableStateFlow<List<CandidateAddress>>(emptyList())
    val addressCandidates: StateFlow<List<CandidateAddress>> = _addressCandidates.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isFavoritesOnly = MutableStateFlow(false)
    val isFavoritesOnly: StateFlow<Boolean> = _isFavoritesOnly.asStateFlow()

    private val _currentLat = MutableStateFlow(37.7749)
    val currentLat: StateFlow<Double> = _currentLat.asStateFlow()

    private val _currentLon = MutableStateFlow(-122.4194)
    val currentLon: StateFlow<Double> = _currentLon.asStateFlow()

    private val favoritesSet = mutableSetOf<String>()
    private var allFetchedPlaces = listOf<PlaceItem>()

    init {
        observeFavoritesFromRoom()
        initCurrentLocationAndFetch()
    }

    private fun observeFavoritesFromRoom() {
        viewModelScope.launch {
            favoritePlaceDao.getAllFavoritePlaceIds().collect { favoriteIds ->
                favoritesSet.clear()
                favoritesSet.addAll(favoriteIds)
                allFetchedPlaces = allFetchedPlaces.map { p ->
                    p.copy(isFavorite = favoritesSet.contains(p.id))
                }
                applyFilter()
            }
        }
    }

    private fun initCurrentLocationAndFetch() {
        viewModelScope.launch {
            val userLocation = locationProvider.getCurrentLocation()
            if (userLocation != null) {
                _currentLat.value = userLocation.latitude
                _currentLon.value = userLocation.longitude
                _locationQuery.value = locationProvider.getAddressFromLocation(userLocation)
            } else {
                _locationQuery.value = "San Francisco, CA"
            }
            fetchNearbyPlaces()
        }
    }

    fun onLocationQueryChanged(query: String) {
        _locationQuery.value = query

        val trimmed = query.trim()
        if (trimmed.length >= 3) {
            fetchAddressCandidates(trimmed)
        } else {
            _addressCandidates.value = emptyList()
        }
    }

    fun clearLocationQuery() {
        _locationQuery.value = ""
        _addressCandidates.value = emptyList()
    }

    fun selectCandidateAddress(candidate: CandidateAddress) {
        _locationQuery.value = candidate.fullAddress
        _addressCandidates.value = emptyList()
        _currentLat.value = candidate.latitude
        _currentLon.value = candidate.longitude
        fetchNearbyPlacesForLocation(candidate.latitude, candidate.longitude)
    }

    private fun fetchAddressCandidates(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(query, 5)
                if (results != null) {
                    val candidateList = results.mapNotNull { addr ->
                        val line = addr.getAddressLine(0) ?: addr.locality ?: addr.featureName
                        if (line != null) {
                            CandidateAddress(
                                fullAddress = line,
                                latitude = addr.latitude,
                                longitude = addr.longitude
                            )
                        } else null
                    }
                    _addressCandidates.value = candidateList
                } else {
                    _addressCandidates.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("NearbyPlacesViewModel", "Address candidate error", e)
                _addressCandidates.value = emptyList()
            }
        }
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
        applyFilter()
    }

    fun toggleGlobalFavoritesFilter() {
        _isFavoritesOnly.value = !_isFavoritesOnly.value
        applyFilter()
    }

    fun toggleFavorite(placeId: String) {
        viewModelScope.launch {
            val isFav = favoritesSet.contains(placeId)
            if (isFav) {
                favoritePlaceDao.deleteFavoriteById(placeId)
            } else {
                val targetPlace = allFetchedPlaces.find { it.id == placeId }
                if (targetPlace != null) {
                    favoritePlaceDao.insertFavorite(
                        FavoritePlaceEntity(
                            placeId = targetPlace.id,
                            name = targetPlace.name,
                            category = targetPlace.category,
                            rating = targetPlace.rating,
                            address = targetPlace.address,
                            latitude = targetPlace.latitude,
                            longitude = targetPlace.longitude
                        )
                    )
                } else {
                    favoritePlaceDao.insertFavorite(
                        FavoritePlaceEntity(
                            placeId = placeId,
                            name = "Favorite Place",
                            category = "Local",
                            rating = 4.5,
                            address = "Saved Location",
                            latitude = _currentLat.value,
                            longitude = _currentLon.value
                        )
                    )
                }
            }
        }
    }

    fun searchLocationAddress() {
        val query = _locationQuery.value.trim()
        _addressCandidates.value = emptyList()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = PlacesUiState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 1)
                    if (addresses?.isNotEmpty() == true) {
                        val address = addresses[0]
                        _currentLat.value = address.latitude
                        _currentLon.value = address.longitude
                        fetchNearbyPlacesForLocation(address.latitude, address.longitude)
                    } else {
                        fetchNearbyPlacesForLocation(_currentLat.value, _currentLon.value)
                    }
                } catch (e: Exception) {
                    Log.e("NearbyPlacesViewModel", "Geocoding error", e)
                    fetchNearbyPlacesForLocation(_currentLat.value, _currentLon.value)
                }
            }
        }
    }

    fun fetchNearbyPlaces() {
        fetchNearbyPlacesForLocation(_currentLat.value, _currentLon.value)
    }

    private fun fetchNearbyPlacesForLocation(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.value = PlacesUiState.Loading
            try {
                val userLocation = Location("UserLoc").apply {
                    latitude = lat
                    longitude = lon
                }

                withContext(Dispatchers.IO) {
                    val placesList = fetchFromNewGooglePlacesApi(lat, lon, DEFAULT_PLACES_API_KEY, userLocation)
                    allFetchedPlaces = placesList
                    withContext(Dispatchers.Main) {
                        applyFilter()
                    }
                }
            } catch (e: Exception) {
                Log.e("NearbyPlacesViewModel", "Error fetching places", e)
                allFetchedPlaces = getFallbackPlaces(lat, lon)
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        val category = _selectedCategory.value
        val favOnly = _isFavoritesOnly.value

        val filtered = allFetchedPlaces.filter { place ->
            val matchesFavGlobal = !favOnly || place.isFavorite
            val matchesCategory = when (category) {
                "All" -> true
                "Favorites Only" -> place.isFavorite
                else -> place.category.equals(category, ignoreCase = true)
            }

            matchesFavGlobal && matchesCategory
        }

        _uiState.value = PlacesUiState.Success(filtered)
    }

    private fun fetchFromNewGooglePlacesApi(lat: Double, lon: Double, apiKey: String, userLocation: Location?): List<PlaceItem> {
        return try {
            val url = URL("https://places.googleapis.com/v1/places:searchNearby")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Goog-Api-Key", apiKey)
            conn.setRequestProperty(
                "X-Goog-FieldMask",
                "places.id,places.displayName,places.primaryType,places.rating,places.formattedAddress,places.location"
            )
            conn.doOutput = true

            val requestBody = JSONObject().apply {
                put("includedTypes", org.json.JSONArray(listOf("restaurant", "cafe", "bakery", "park")))
                put("maxResultCount", 15)
                put("locationRestriction", JSONObject().apply {
                    put("circle", JSONObject().apply {
                        put("center", JSONObject().apply {
                            put("latitude", lat)
                            put("longitude", lon)
                        })
                        put("radius", 3000.0)
                    })
                })
            }

            conn.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("NearbyPlacesViewModel", "New Places API HTTP $responseCode: $errText")
                return fetchFromLegacyGooglePlacesApi(lat, lon, apiKey, userLocation)
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val placesArray = json.optJSONArray("places") ?: return fetchFromLegacyGooglePlacesApi(lat, lon, apiKey, userLocation)

            val list = mutableListOf<PlaceItem>()
            for (i in 0 until placesArray.length()) {
                val item = placesArray.getJSONObject(i)
                val id = item.optString("id", i.toString())

                val displayNameObj = item.optJSONObject("displayName")
                val name = displayNameObj?.optString("text") ?: "Local Spot"

                val rating = item.optDouble("rating", 4.5)
                val address = item.optString("formattedAddress", "Nearby Address")
                val primaryType = item.optString("primaryType", "restaurant")
                val category = when {
                    primaryType.contains("cafe", ignoreCase = true) -> "Cafe"
                    primaryType.contains("bakery", ignoreCase = true) -> "Bakery"
                    primaryType.contains("park", ignoreCase = true) -> "Park"
                    else -> "Restaurant"
                }

                val locationObj = item.optJSONObject("location")
                val placeLat = locationObj?.optDouble("latitude", lat) ?: lat
                val placeLon = locationObj?.optDouble("longitude", lon) ?: lon

                val distanceStr = calculateDistanceString(userLocation, placeLat, placeLon)

                list.add(
                    PlaceItem(
                        id = id,
                        name = name,
                        category = category,
                        rating = rating,
                        distance = distanceStr,
                        address = address,
                        latitude = placeLat,
                        longitude = placeLon,
                        isFavorite = favoritesSet.contains(id)
                    )
                )
            }
            if (list.isNotEmpty()) list else fetchFromLegacyGooglePlacesApi(lat, lon, apiKey, userLocation)
        } catch (e: Exception) {
            Log.e("NearbyPlacesViewModel", "New Places API Exception, fallback to Legacy", e)
            fetchFromLegacyGooglePlacesApi(lat, lon, apiKey, userLocation)
        }
    }

    private fun fetchFromLegacyGooglePlacesApi(lat: Double, lon: Double, apiKey: String, userLocation: Location?): List<PlaceItem> {
        return try {
            val urlString = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$lat,$lon&radius=3000&type=restaurant&key=$apiKey"

            val responseText = URL(urlString).readText()
            val json = JSONObject(responseText)
            val results = json.optJSONArray("results") ?: return getFallbackPlaces(lat, lon)

            val list = mutableListOf<PlaceItem>()
            for (i in 0 until results.length().coerceAtMost(15)) {
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
                        latitude = placeLat,
                        longitude = placeLon,
                        isFavorite = favoritesSet.contains(id)
                    )
                )
            }
            if (list.isNotEmpty()) list else getFallbackPlaces(lat, lon)
        } catch (e: Exception) {
            getFallbackPlaces(lat, lon)
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

    private fun getFallbackPlaces(baseLat: Double = 37.7749, baseLon: Double = -122.4194): List<PlaceItem> {
        return listOf(
            PlaceItem("1", "Blue Bottle Coffee", "Cafe", 4.7, "250m", "123 Main St", baseLat + 0.002, baseLon + 0.002, favoritesSet.contains("1")),
            PlaceItem("2", "Gourmet Burger Kitchen", "Restaurant", 4.5, "450m", "45 Park Ave", baseLat - 0.003, baseLon + 0.001, favoritesSet.contains("2")),
            PlaceItem("3", "Artisan Bakery & Pastry", "Bakery", 4.8, "600m", "78 Baker St", baseLat + 0.004, baseLon - 0.002, favoritesSet.contains("3")),
            PlaceItem("4", "Central Community Park", "Park", 4.6, "800m", "200 Green Way", baseLat - 0.002, baseLon - 0.004, favoritesSet.contains("4")),
            PlaceItem("5", "Espresso Express Cafe", "Cafe", 4.3, "300m", "15 High St", baseLat + 0.001, baseLon - 0.003, favoritesSet.contains("5")),
            PlaceItem("6", "Tokyo Ramen House", "Restaurant", 4.9, "950m", "89 Noodle Lane", baseLat - 0.004, baseLon + 0.003, favoritesSet.contains("6"))
        )
    }
}
