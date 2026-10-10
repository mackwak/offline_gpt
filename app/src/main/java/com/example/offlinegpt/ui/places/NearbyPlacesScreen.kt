package com.example.offlinegpt.ui.places

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

enum class PlacesViewMode {
    LIST, MAP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyPlacesScreen(
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    viewModel: NearbyPlacesViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val locationQuery by viewModel.locationQuery.collectAsState()
    val addressCandidates by viewModel.addressCandidates.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isFavoritesOnly by viewModel.isFavoritesOnly.collectAsState()
    val currentLat by viewModel.currentLat.collectAsState()
    val currentLon by viewModel.currentLon.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var viewMode by remember { mutableStateOf(PlacesViewMode.LIST) }
    var selectedPlace by remember { mutableStateOf<PlaceItem?>(null) }

    val categories = listOf("All", "Cafe", "Restaurant", "Bakery", "Park")

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(currentLat, currentLon), 14f)
    }

    // Safely initialize Google Maps Native SDK
    LaunchedEffect(context) {
        runCatching {
            if (isFavorite) {
                viewModel.toggleGlobalFavoritesFilter()
            }
            MapsInitializer.initialize(context)
        }.onFailure {
            Log.e("NearbyPlacesScreen", "MapsInitializer error", it)
        }
    }

    // Animate map camera smoothly whenever currentLat or currentLon changes
    LaunchedEffect(currentLat, currentLon) {
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(currentLat, currentLon), 14f)
            )
        }.onFailure {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(currentLat, currentLon), 14f)
        }


    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFavorite) "Favorites" else "Nearby Spots") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {/*
                    FilterChip(
                        selected = isFavoritesOnly,
                        onClick = { viewModel.toggleGlobalFavoritesFilter() },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorites Only",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
*/
                    Spacer(modifier = Modifier.width(4.dp))

                    // View Mode Switcher (List vs Map)
                    IconButton(
                        onClick = {
                            viewMode = if (viewMode == PlacesViewMode.LIST) PlacesViewMode.MAP else PlacesViewMode.LIST
                        }
                    ) {
                        Icon(
                            imageVector = if (viewMode == PlacesViewMode.LIST) Icons.Default.Map else Icons.AutoMirrored.Filled.List,
                            contentDescription = "Switch View Mode"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isFavorite) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = locationQuery,
                                onValueChange = { viewModel.onLocationQueryChanged(it) },
                                label = { Text("Search City / Address (3+ chars)") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (locationQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.clearLocationQuery() }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear Address")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            Button(
                                onClick = { viewModel.searchLocationAddress() },
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("Search")
                            }
                        }

                        // Candidate Address Dropdown List when 3+ characters are typed
                        if (addressCandidates.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    addressCandidates.forEach { candidate ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.selectCandidateAddress(candidate) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = candidate.fullAddress,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Categories Filter Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.onCategorySelected(cat) },
                        label = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            when (val state = uiState) {
                is PlacesUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Fetching places near location...")
                        }
                    }
                }
                is PlacesUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is PlacesUiState.Success -> {
                    if (viewMode == PlacesViewMode.MAP) {
                        // Google Map View with Category-Colored Pins & Interactive Bottom Overlay Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState
                            ) {
                                state.places.forEach { place ->
                                    val markerHue = getCategoryMarkerHue(place.category)
                                    Marker(
                                        state = rememberMarkerState(
                                            position = LatLng(place.latitude, place.longitude)
                                        ),
                                        title = place.name,
                                        snippet = "${place.category} • ⭐ ${place.rating}",
                                        icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                                        onClick = {
                                            selectedPlace = place
                                            false
                                        }
                                    )
                                }
                            }

                            // Info Overlay Card when a Pin is selected
                            selectedPlace?.let { place ->
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = place.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )

                                            IconButton(onClick = { selectedPlace = null }) {
                                                Icon(Icons.Default.Close, contentDescription = "Close Popup")
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "⭐ ${place.rating} • ${place.distance}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(place.category, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }

                                        Text(
                                            text = place.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.toggleFavorite(place.id)
                                                    selectedPlace = place.copy(isFavorite = !place.isFavorite)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (place.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = "Favorite",
                                                    tint = if (place.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Button(
                                                onClick = { launchNavigationApp(context, place) }
                                            ) {
                                                Icon(Icons.Default.Navigation, contentDescription = "Navigate")
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Open Navigation App")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // List View
                        Text(
                            text = if (isFavorite) "Favorites (${state.places.size})" else "Nearby Spots (${state.places.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (state.places.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isFavorite) "No favorite places found." else "No nearby spots found.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.places, key = { it.id }) { place ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = place.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${place.address} • ${place.distance}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "⭐ ${place.rating}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    SuggestionChip(
                                                        onClick = { },
                                                        label = { Text(place.category, style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { launchNavigationApp(context, place) }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Navigation,
                                                        contentDescription = "Navigate",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.toggleFavorite(place.id) }
                                                ) {
                                                    Icon(
                                                        imageVector = if (place.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        contentDescription = "Favorite",
                                                        tint = if (place.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryMarkerHue(category: String): Float {
    return when (category.lowercase()) {
        "cafe" -> BitmapDescriptorFactory.HUE_ORANGE
        "restaurant" -> BitmapDescriptorFactory.HUE_RED
        "bakery" -> BitmapDescriptorFactory.HUE_YELLOW
        "park" -> BitmapDescriptorFactory.HUE_GREEN
        else -> BitmapDescriptorFactory.HUE_AZURE
    }
}

private fun launchNavigationApp(context: Context, place: PlaceItem) {
    val geoUri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}
