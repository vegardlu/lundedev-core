package no.lundedev.core.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.lundedev.core.service.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(DashboardController::class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var lightService: LightService

    @Autowired
    private lateinit var weatherService: WeatherService

    @Autowired
    private lateinit var sensorService: SensorService

    @Autowired
    private lateinit var blindService: BlindService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @TestConfiguration
    class Config {
        @Bean fun lightService() = mockk<LightService>(relaxed = true)
        @Bean fun weatherService() = mockk<WeatherService>(relaxed = true)
        @Bean fun sensorService() = mockk<SensorService>(relaxed = true)
        @Bean fun blindService() = mockk<BlindService>(relaxed = true)
    }

    @Test
    @WithMockUser
    fun `getLights should return list of lights`() {
        val lights = listOf(LightDto("light.1", "Living Room", true, 255, null))
        every { lightService.getLights() } returns lights

        mockMvc.perform(get("/api/dashboard/lights"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$[0].id").value("light.1"))
    }

    @Test
    @WithMockUser
    fun `toggleLight should call lightService`() {
        mockMvc.perform(post("/api/dashboard/lights/light.1/toggle").with(csrf()))
            .andExpect(status().isOk)

        verify { lightService.toggleLight("light.1") }
    }

    @Test
    @WithMockUser
    fun `updateLight should call lightService with command`() {
        val cmd = UpdateLightCommand(isOn = true, brightness = 150)
        mockMvc.perform(
            post("/api/dashboard/lights/light.2/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cmd))
                .with(csrf())
        )
            .andExpect(status().isOk)

        verify { lightService.updateLight("light.2", cmd) }
    }

    @Test
    @WithMockUser
    fun `getWeather should return list of weather states`() {
        val weatherList = listOf(WeatherDto("Oslo", 15.0, "clearsky", 0.0))
        every { weatherService.getWeather() } returns weatherList

        mockMvc.perform(get("/api/dashboard/weather"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$[0].location").value("Oslo"))
            .andExpect(jsonPath("\$[0].temperature").value(15.0))
            .andExpect(jsonPath("\$[0].symbolCode").value("clearsky"))
    }

    @Test
    @WithMockUser
    fun `getSensors should return sensors`() {
        val sensors = listOf(SensorDto("sensor.temp", "Temperature", "20.5", "C", "temperature", "Living Room", "1"))
        every { sensorService.getSensors() } returns sensors

        mockMvc.perform(get("/api/dashboard/sensors"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$[0].id").value("sensor.temp"))
            .andExpect(jsonPath("\$[0].state").value("20.5"))
    }

    @Test
    @WithMockUser
    fun `blinds endpoints should delegate to blindService`() {
        mockMvc.perform(post("/api/dashboard/blinds/blind.1/open").with(csrf())).andExpect(status().isOk)
        verify { blindService.open("blind.1") }

        mockMvc.perform(post("/api/dashboard/blinds/blind.1/close").with(csrf())).andExpect(status().isOk)
        verify { blindService.close("blind.1") }

        mockMvc.perform(post("/api/dashboard/blinds/blind.1/stop").with(csrf())).andExpect(status().isOk)
        verify { blindService.stop("blind.1") }

        val cmd = SetBlindPositionCommand(75)
        mockMvc.perform(
            post("/api/dashboard/blinds/blind.1/position")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cmd))
                .with(csrf())
        ).andExpect(status().isOk)
        verify { blindService.setPosition("blind.1", 75) }
    }
}
