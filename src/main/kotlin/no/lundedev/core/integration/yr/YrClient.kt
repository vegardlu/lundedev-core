package no.lundedev.core.integration.yr

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class YrClient(
    builder: RestClient.Builder,
    properties: YrProperties
) {
    private val logger = LoggerFactory.getLogger(YrClient::class.java)
    private val client: RestClient = builder
        .baseUrl(properties.url)
        .defaultHeader("User-Agent", properties.userAgent)
        .defaultHeader("Accept", "application/json")
        .build()

    fun getForecast(lat: Double, lon: Double): YrLocationForecastResponse? {
        logger.info("Fetching weather forecast for lat={}, lon={}", lat, lon)
        return try {
            client.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/compact")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .build()
                }
                .retrieve()
                .body(YrLocationForecastResponse::class.java)
        } catch (e: Exception) {
            logger.error("Failed to fetch weather forecast for lat={}, lon={}", lat, lon, e)
            null
        }
    }
}

data class YrLocationForecastResponse(
    val properties: YrForecastProperties
)

data class YrForecastProperties(
    val timeseries: List<YrTimeSeries>
)

data class YrTimeSeries(
    val time: String,
    val data: YrData
)

data class YrData(
    val instant: YrInstant,
    val next_1_hours: YrNext1Hours? = null,
    val next_6_hours: YrNext6Hours? = null
)

data class YrInstant(
    val details: YrInstantDetails
)

data class YrInstantDetails(
    val air_temperature: Double
)

data class YrNext1Hours(
    val summary: YrSummary,
    val details: YrNext1HoursDetails? = null
)

data class YrNext1HoursDetails(
    val precipitation_amount: Double? = null,
    val precipitation_amount_max: Double? = null,
    val precipitation_amount_min: Double? = null
)

data class YrNext6Hours(
    val summary: YrSummary
)

data class YrSummary(
    val symbol_code: String
)
