package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeAssistantToolConfigTest {

    private val homeAssistantService: HomeAssistantService = mockk()
    private val toolConfig = HomeAssistantToolConfig(homeAssistantService)

    @Test
    fun `list_areas should call service and return string`() {
        every { homeAssistantService.listAreas() } returns listOf("Living Room", "Kitchen")
        
        val result = toolConfig.execute("list_areas", emptyMap())
        
        assertEquals("Living Room\nKitchen", result)
        verify { homeAssistantService.listAreas() }
    }

    @Test
    fun `list_entities should call service with filters`() {
        every { homeAssistantService.listEntities("light", "Living Room") } returns listOf("light.living_room")
        
        val result = toolConfig.execute("list_entities", mapOf("domain" to "light", "area" to "Living Room"))
        
        assertEquals("light.living_room", result)
        verify { homeAssistantService.listEntities("light", "Living Room") }
    }

    @Test
    fun `list_entities should return all entities if filter yields empty result`() {
        every { homeAssistantService.listEntities("light", "Attic") } returns emptyList()
        every { homeAssistantService.listEntities(null, null) } returns listOf("light.living_room", "switch.kitchen")
        
        val result = toolConfig.execute("list_entities", mapOf("domain" to "light", "area" to "Attic"))
        
        assertTrue(result.contains("No entities found with filter"))
        assertTrue(result.contains("light.living_room"))
        verify { homeAssistantService.listEntities("light", "Attic") }
        verify { homeAssistantService.listEntities(null, null) }
    }

    @Test
    fun `get_state should return entity state`() {
        every { homeAssistantService.getState("light.living_room") } returns mapOf("state" to "on", "attributes" to mapOf<String, Any>())
        
        val result = toolConfig.execute("get_state", mapOf("entity_id" to "light.living_room"))
        
        assertEquals("{state=on, attributes={}}", result)
        verify { homeAssistantService.getState("light.living_room") }
    }

    @Test
    fun `call_service should execute service call`() {
        every { homeAssistantService.callService(any(), any(), any(), any()) } returns Unit
        
        val result = toolConfig.execute("call_service", mapOf(
            "domain" to "light",
            "service" to "turn_on",
            "entity_id" to "light.living_room",
            "payload_json" to "{\"brightness\": 255}"
        ))
        
        assertTrue(result.contains("Service light.turn_on called"))
        verify { 
            homeAssistantService.callService(
                "light", 
                "turn_on", 
                "light.living_room", 
                mapOf("brightness" to 255)
            ) 
        }
    }
}
