package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.lundedev.core.integration.homeassistant.EnhancedEntityState
import no.lundedev.core.integration.homeassistant.EntityState
import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import no.lundedev.core.integration.homeassistant.HomeAssistantCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeAssistantMcpServiceTest {

    private val client = mockk<HomeAssistantClient>(relaxed = true)
    private val cache = mockk<HomeAssistantCache>(relaxed = true)
    private val service = HomeAssistantMcpService(client, cache)

    @Test
    fun `listEntities should return formatted strings`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap()),
            EnhancedEntityState("switch.living_room", "Living Room Switch", "living_room", null, "First Floor", "off", mapOf("device_class" to "outlet"))
        )
        // Ensure mock accepts both arguments, defaulting to null
        every { cache.getEntities(null, null) } returns entities

        val result = service.listEntities()

        assertEquals(2, result.size)
        // Expected format: entity_id|friendly_name|area|floor|state|device_class|unit
        // Note: area/floor default to "None" if null. attributes defaults to "" if not found.
        assertEquals("light.kitchen|Kitchen Light|Kitchen|First Floor|on||", result[0])
        assertEquals("switch.living_room|Living Room Switch|None|First Floor|off|outlet|", result[1])
    }

    @Test
    fun `listEntities should pass domain and area filter`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap())
        )
        every { cache.getEntities("light", "Kitchen") } returns entities

        val result = service.listEntities("light", "Kitchen")

        assertEquals(1, result.size)
        assertEquals("light.kitchen|Kitchen Light|Kitchen|First Floor|on||", result[0])
        verify { cache.getEntities("light", "Kitchen") }
    }
    
    @Test
    fun `listAreas should return list of strings`() {
        val areas = listOf("Kitchen", "Living Room")
        every { cache.getAreas() } returns areas
        
        val result = service.listAreas()
        
        assertEquals(2, result.size)
        assertTrue(result.contains("Kitchen"))
        assertTrue(result.contains("Living Room"))
        verify { cache.getAreas() }
    }

    @Test
    fun `getState should return entity state`() {
        val entityId = "light.kitchen"
        val state = EnhancedEntityState(entityId, "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", mapOf("brightness" to 255))
        every { cache.getEntity(entityId) } returns state

        val result = service.getState(entityId)

        assertTrue(result != null)
        assertEquals("on", result!!["state"])
        assertEquals(mapOf("brightness" to 255), result["attributes"])
    }

    @Test
    fun `callService should delegate to client`() {
        // service.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100))
        // This test relies on client mock which is relaxed, so just verify call
        service.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100))

        verify { client.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100)) }
    }
}
