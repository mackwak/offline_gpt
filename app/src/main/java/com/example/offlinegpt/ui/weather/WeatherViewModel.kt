package com.example.offlinegpt.ui.weather

import android.content.Context
import android.location.Geocoder
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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ForecastItem(
    val day: String,
    val condition: String,
    val icon: String,
    val tempHigh: String,
    val tempLow: String
)

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(
        val city: String,
        val locationName: String,
        val currentTemp: String,
        val condition: String,
        val icon: String,
        val humidity: String,
        val windSpeed: String,
        val forecastList: List<ForecastItem>
    ) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        fetchWeather()
    }

    fun fetchWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            try {
                val location = locationProvider.getCurrentLocation()
                val lat = location?.latitude ?: 37.7749
                val lon = location?.longitude ?: -122.4194

                val address = if (location != null) {
                    locationProvider.getAddressFromLocation(location)
                } else {
                    "Current Location"
                }

                val city = extractCityName(lat, lon, address)

                withContext(Dispatchers.IO) {
                    val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto"
                    val responseText = URL(urlString).readText()
                    val json = JSONObject(responseText)

                    val currentWeather = json.getJSONObject("current_weather")
                    val temp = currentWeather.getDouble("temperature")
                    val wind = currentWeather.getDouble("windspeed")
                    val weatherCode = currentWeather.getInt("weathercode")

                    val (conditionText, icon) = getWeatherCondition(weatherCode)

                    val daily = json.optJSONObject("daily")
                    val forecastItems = mutableListOf<ForecastItem>()

                    if (daily != null) {
                        val times = daily.getJSONArray("time")
                        val codes = daily.getJSONArray("weathercode")
                        val maxTemps = daily.getJSONArray("temperature_2m_max")
                        val minTemps = daily.getJSONArray("temperature_2m_min")

                        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())

                        for (i in 0 until times.length().coerceAtMost(7)) {
                            val timeStr = times.getString(i)
                            val code = codes.getInt(i)
                            val high = "${maxTemps.getDouble(i).toInt()}°"
                            val low = "${minTemps.getDouble(i).toInt()}°"
                            val (fCondition, fIcon) = getWeatherCondition(code)

                            val dayLabel = if (i == 0) "Today" else if (i == 1) "Tomorrow" else {
                                val date = inputFormat.parse(timeStr)
                                if (date != null) outputFormat.format(date) else timeStr
                            }

                            forecastItems.add(
                                ForecastItem(
                                    day = dayLabel,
                                    condition = fCondition,
                                    icon = fIcon,
                                    tempHigh = high,
                                    tempLow = low
                                )
                            )
                        }
                    }

                    _uiState.value = WeatherUiState.Success(
                        city = city,
                        locationName = address,
                        currentTemp = "${temp.toInt()}°C",
                        condition = conditionText,
                        icon = icon,
                        humidity = "60%",
                        windSpeed = "${wind.toInt()} km/h",
                        forecastList = forecastItems
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("Failed to fetch weather: ${e.localizedMessage}")
            }
        }
    }

    private fun extractCityName(lat: Double, lon: Double, addressLine: String): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (addresses?.isNotEmpty() == true) {
                val addr = addresses[0]
                addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addressLine
            } else {
                addressLine
            }
        } catch (_: Exception) {
            addressLine
        }
    }

    private fun getWeatherCondition(code: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("Clear Sky", "☀️")
            1, 2 -> Pair("Partly Cloudy", "⛅")
            3 -> Pair("Overcast", "☁️")
            45, 48 -> Pair("Foggy", "🌫️")
            51, 53, 55, 61, 63, 65 -> Pair("Rain Showers", "🌧️")
            71, 73, 75 -> Pair("Snow", "❄️")
            80, 81, 82 -> Pair("Heavy Rain", "🌧️")
            95, 96, 99 -> Pair("Thunderstorm", "🌩️")
            else -> Pair("Clear", "☀️")
        }
    }
}
