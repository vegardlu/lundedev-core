package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.junit.jupiter.api.Test

class LightServiceTest {

    private val homeAssistantClient = mockk<HomeAssistantClient>(relaxed = true)
    private val lightService = LightService(homeAssistantClient)

    @Test
    fun `toggleLight should call toggle service`() {
        val lightId = "light.living_room"
        
        lightService.toggleLight(lightId)

        verify { homeAssistantClient.callService("light", "toggle", lightId) }
    }

    @Test
    fun `updateLight should call turn_on service with brightness`() {
        val lightId = "light.kitchen"
        val cmd = UpdateLightCommand(isOn = true, brightness = 150)

        lightService.updateLight(lightId, cmd)

        verify { 
            homeAssistantClient.callService("light", "turn_on", lightId, emptyMap())
            homeAssistantClient.callService("light", "turn_on", lightId, mapOf("brightness" to 150))
        }
    }
    
    @Test
    fun `updateLight should call turn_on service with color`() {
        val lightId = "light.bedroom"
        val color = listOf(255, 0, 0)
        val cmd = UpdateLightCommand(color = color)

        lightService.updateLight(lightId, cmd)

        // isOn default null, brightness null
        verify { homeAssistantClient.callService("light", "turn_on", lightId, mapOf("rgb_color" to color)) }
    }

    @Test
    fun `updateLight should call turn_off service`() {
        val lightId = "light.hallway"
        val cmd = UpdateLightCommand(isOn = false)

        lightService.updateLight(lightId, cmd)

        verify { homeAssistantClient.callService("light", "turn_off", lightId) }
    }
}
