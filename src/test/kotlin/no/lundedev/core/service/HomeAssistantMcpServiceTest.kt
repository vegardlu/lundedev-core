package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.lundedev.core.integration.homeassistant.EnhancedEntityState
import no.lundedev.core.integration.homeassistant.EntityState
import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeAssistantMcpServiceTest {

    private val client = mockk<HomeAssistantClient>()
    private val service = HomeAssistantMcpService(client)

    @Test
    fun `listEntities should return formatted strings`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "Kitchen", "First Floor", "on", emptyMap()),
            EnhancedEntityState("switch.living_room", "Living Room Switch", null, null, "off", mapOf("device_class" to "outlet"))
        )
        // Ensure mock accepts both arguments, defaulting to null
        every { client.getEntitiesWithArea(null, null) } returns entities

        val result = service.listEntities()

        assertEquals(2, result.size)
        // Expected format: entity_id|friendly_name|area|floor|state|device_class|unit
        // Note: area/floor default to "None" if null. attributes defaults to "" if not found.
        assertEquals("light.kitchen|Kitchen Light|Kitchen|First Floor|on||", result[0])
        assertEquals("switch.living_room|Living Room Switch|None|None|off|outlet|", result[1])
    }

    @Test
    fun `listEntities should pass domain and area filter`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "Kitchen", "First Floor", "on", emptyMap())
        )
        every { client.getEntitiesWithArea("light", "Kitchen") } returns entities

        val result = service.listEntities("light", "Kitchen")

        assertEquals(1, result.size)
        assertEquals("light.kitchen|Kitchen Light|Kitchen|First Floor|on||", result[0])
        verify { client.getEntitiesWithArea("light", "Kitchen") }
    }
    
    @Test
    fun `listAreas should return list of strings`() {
        val areas = listOf("Kitchen", "Living Room")
        every { client.getAreas() } returns areas
        
        val result = service.listAreas()
        
        assertEquals(2, result.size)
        assertTrue(result.contains("Kitchen"))
        assertTrue(result.contains("Living Room"))
        verify { client.getAreas() }
    }

    @Test
    fun `getState should return entity state`() {
        val entityId = "light.kitchen"
        val state = EntityState(entityId, "on", mapOf("brightness" to 255))
        every { client.getStates() } returns listOf(state)

        val result = service.getState(entityId)

        assertTrue(result != null)
        assertEquals("on", result!!["state"])
        assertEquals(mapOf("brightness" to 255), result["attributes"])
    }

    @Test
    fun `callService should delegate to client`() {
        every { client.callService(any(), any(), any(), any()) } returns Unit

        service.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100))

        verify { client.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100)) }
    }
}
