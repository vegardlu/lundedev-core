package no.lundedev.core.service

data class WeatherDto(
    val location: String,
    val temperature: Double,
    val symbolCode: String,
    val precipitationAmount: Double?
)
