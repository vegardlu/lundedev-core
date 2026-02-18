package no.lundedev.core.service

import no.lundedev.core.integration.yr.YrClient
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime

@Service
class WeatherService(
    private val yrClient: YrClient
) {
    private val locations = listOf(
        Location("Fyrstikkalléen 1", 59.9139, 10.7952),
        Location("Vidjeveien 4a", 59.8880, 10.8037)
    )

    // Simple in-memory cache: Location Name -> (Timestamp, WeatherDto)
    private val cache = ConcurrentHashMap<String, Pair<LocalDateTime, WeatherDto>>()
    private val cacheDurationMinutes = 30L

    fun getWeather(): List<WeatherDto> =
        locations.map { location ->
            getCachedOrFetch(location)
        }

    private fun getCachedOrFetch(location: Location): WeatherDto {
        val now = LocalDateTime.now()
        val cached = cache[location.name]

        if (cached != null && cached.first.plusMinutes(cacheDurationMinutes).isAfter(now)) {
            return cached.second
        }

        val forecast = yrClient.getForecast(location.lat, location.lon)
        val weatherDto = if (forecast != null) {
            val current = forecast.properties.timeseries.firstOrNull()?.data
            val nextHour = current?.next_1_hours?.summary?.symbol_code
                ?: current?.next_6_hours?.summary?.symbol_code // Fallback
                ?: "unknown"
            
            WeatherDto(
                location = location.name,
                temperature = current?.instant?.details?.air_temperature ?: 0.0,
                symbolCode = nextHour,
                precipitationAmount = current?.next_1_hours?.details?.precipitation_amount
            )
        } else {
             WeatherDto(
                location = location.name,
                temperature = 0.0,
                symbolCode = "unknown", 
                precipitationAmount = null
             )
        }

        cache[location.name] = Pair(now, weatherDto)
        return weatherDto
    }

    data class Location(val name: String, val lat: Double, val lon: Double)
}
