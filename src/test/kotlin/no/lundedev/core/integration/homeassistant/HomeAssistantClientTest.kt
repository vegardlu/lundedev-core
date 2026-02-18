package no.lundedev.core.integration.homeassistant

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class HomeAssistantClientTest {

    private val builder: RestClient.Builder = mockk(relaxed = true)
    private val client: RestClient = mockk(relaxed = true)
    private val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
    private val requestHeadersSpec: RestClient.RequestHeadersSpec<*> = mockk(relaxed = true)
    private val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)

    private val properties = HomeAssistantProperties(
        url = "http://localhost:8123",
        token = "token"
    )

    @Test
    fun `getEntitiesWithArea should fall back to getStates if template returns empty`() {
        // Setup mock chain for RestClient
        every { builder.baseUrl(any<String>()) } returns builder
        every { builder.defaultHeader(any<String>(), any<String>()) } returns builder
        every { builder.build() } returns client

        // Mock template call failing (returning empty string or causing error)
        every { client.post() } returns mockk {
            every { uri("/api/template") } returns mockk {
                every { body(any<Map<String, String>>()) } returns mockk {
                    every { retrieve() } returns mockk {
                        every { body(String::class.java) } returns "" // Empty response triggers fallback
                    }
                }
            }
        }

        // Mock getStates call for fallback
        val states = arrayOf(
            EntityState("light.kitchen", "on", mapOf("friendly_name" to "Kitchen Light")),
            EntityState("sensor.temp", "20", mapOf("unit_of_measurement" to "C"))
        )
        
        every { client.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri("/api/states") } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Array<EntityState>::class.java) } returns states

        // Create SUT
        val homeAssistantClient = HomeAssistantClient(builder, properties)
        
        // Execute
        val result = homeAssistantClient.getEntitiesWithArea()

        // Verify
        assertEquals(2, result.size)
        assertEquals("light.kitchen", result[0].entity_id)
        assertEquals("Kitchen Light", result[0].friendly_name)
        assertEquals(null, result[0].area) // Expect null area from fallback
        
        // Verify fallback was called
        verify { requestHeadersUriSpec.uri("/api/states") }
    }
}
