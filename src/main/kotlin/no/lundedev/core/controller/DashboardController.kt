package no.lundedev.core.controller

import no.lundedev.core.service.BlindDto
import no.lundedev.core.service.LightDto
import no.lundedev.core.service.LightService
import no.lundedev.core.service.UpdateLightCommand
import no.lundedev.core.service.SensorService
import no.lundedev.core.service.BlindService
import no.lundedev.core.service.SensorDto
import no.lundedev.core.service.WeatherDto
import no.lundedev.core.service.WeatherService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val lightService: LightService,
    private val weatherService: WeatherService,
    private val sensorService: SensorService,
    private val blindService: BlindService
) {

    @GetMapping("/lights")
    fun getLights(): List<LightDto> {
        return lightService.getLights()
    }

    @GetMapping("/weather")
    fun getWeather(): List<WeatherDto> {
        return weatherService.getWeather()
    }

    @GetMapping("/sensors")
    fun getSensors(): List<SensorDto> {
        return sensorService.getSensors()
    }

    @GetMapping("/blinds")
    fun getBlinds(): List<BlindDto> {
        return blindService.getBlinds()
    }

    @PostMapping("/lights/{id}/toggle")
    fun toggleLight(@PathVariable id: String) {
        lightService.toggleLight(id)
    }

    @PostMapping("/lights/{id}/state")
    fun updateLight(@PathVariable id: String, @RequestBody cmd: UpdateLightCommand) {
        lightService.updateLight(id, cmd)
    }

    @PostMapping("/blinds/{id}/position")
    fun setBlindPosition(@PathVariable id: String, @RequestBody cmd: SetBlindPositionCommand) {
        blindService.setPosition(id, cmd.position)
    }

    @PostMapping("/blinds/{id}/open")
    fun openBlind(@PathVariable id: String) {
        blindService.open(id)
    }

    @PostMapping("/blinds/{id}/close")
    fun closeBlind(@PathVariable id: String) {
        blindService.close(id)
    }

    @PostMapping("/blinds/{id}/stop")
    fun stopBlind(@PathVariable id: String) {
        blindService.stop(id)
    }
}

data class SetBlindPositionCommand(
    val position: Int
)
