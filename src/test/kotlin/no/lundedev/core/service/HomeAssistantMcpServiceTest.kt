package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.lundedev.core.integration.homeassistant.EntityState
import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeAssistantMcpServiceTest {

    private val client = mockk<HomeAssistantClient>()
    private val service = HomeAssistantMcpService(client)

    @Test
    fun `listEntities should return all entities when no domain is provided`() {
        val states = listOf(
            EntityState("light.kitchen", "on"),
            EntityState("switch.living_room", "off"),
            EntityState("sensor.temperature", "22.5")
        )
        every { client.getStates() } returns states

        val result = service.listEntities()

        assertEquals(3, result.size)
        assertEquals(listOf("light.kitchen", "switch.living_room", "sensor.temperature"), result)
    }

    @Test
    fun `listEntities should filter by domain`() {
        val states = listOf(
            EntityState("light.kitchen", "on"),
            EntityState("switch.living_room", "off"),
            EntityState("sensor.temperature", "22.5")
        )
        every { client.getStates() } returns states

        val result = service.listEntities("light")

        assertEquals(1, result.size)
        assertEquals(listOf("light.kitchen"), result)
    }

    @Test
    fun `getState should return entity state`() {
        val entityId = "light.kitchen"
        val state = EntityState(entityId, "on", mapOf("brightness" to 255))
        every { client.getStates() } returns listOf(state)

        val result = service.getState(entityId)

        assertNotNull(result)
        assertEquals("on", result!!["state"])
        assertEquals(mapOf("brightness" to 255), result["attributes"])
    }

    @Test
    fun `getState should return null if entity not found`() {
        every { client.getStates() } returns emptyList()

        val result = service.getState("non.existent")

        assertNull(result)
    }

    @Test
    fun `callService should delegate to client`() {
        every { client.callService(any(), any(), any(), any()) } returns Unit

        service.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100))

        verify { client.callService("light", "turn_on", "light.kitchen", mapOf("brightness" to 100)) }
    }
}
