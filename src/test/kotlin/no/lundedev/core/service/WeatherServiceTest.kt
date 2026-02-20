package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import no.lundedev.core.integration.yr.YrClient
import no.lundedev.core.integration.yr.YrForecastProperties
import no.lundedev.core.integration.yr.YrTimeSeries
import no.lundedev.core.integration.yr.YrLocationForecastResponse
import no.lundedev.core.integration.yr.YrData
import no.lundedev.core.integration.yr.YrInstant
import no.lundedev.core.integration.yr.YrInstantDetails
import no.lundedev.core.integration.yr.YrNext1Hours
import no.lundedev.core.integration.yr.YrSummary
import no.lundedev.core.integration.yr.YrNext1HoursDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeatherServiceTest {

    private val yrClient = mockk<YrClient>()
    private val weatherService = WeatherService(yrClient)

    @Test
    fun `getWeather fetches from yrClient and maps correctly`() {
        val mockResponse = YrLocationForecastResponse(
            properties = YrForecastProperties(
                timeseries = listOf(
                    YrTimeSeries(
                        time = "2026-02-20T12:00:00Z",
                        data = YrData(
                            instant = YrInstant(
                                details = YrInstantDetails(air_temperature = 22.5)
                            ),
                            next_1_hours = YrNext1Hours(
                                summary = YrSummary(symbol_code = "partlycloudy_day"),
                                details = YrNext1HoursDetails(precipitation_amount = 0.5)
                            )
                        )
                    )
                )
            )
        )

        every { yrClient.getForecast(any(), any()) } returns mockResponse

        val weatherList = weatherService.getWeather()

        assertEquals(2, weatherList.size) // Fyrstikkalléen 1 and Vidjeveien 4a

        val firstLoc = weatherList.first()
        assertEquals("Fyrstikkalléen 1", firstLoc.location)
        assertEquals(22.5, firstLoc.temperature)
        assertEquals("partlycloudy_day", firstLoc.symbolCode)
        assertEquals(0.5, firstLoc.precipitationAmount)
    }

    @Test
    fun `getWeather uses fallback when yrClient returns null`() {
        every { yrClient.getForecast(any(), any()) } returns null

        val weatherList = weatherService.getWeather()

        weatherList.forEach {
            assertEquals(0.0, it.temperature)
            assertEquals("unknown", it.symbolCode)
            assertEquals(null, it.precipitationAmount)
        }
    }
}
