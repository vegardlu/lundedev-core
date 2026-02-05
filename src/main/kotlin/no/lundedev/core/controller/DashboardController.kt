package no.lundedev.core.controller

import no.lundedev.core.service.LightDto
import no.lundedev.core.service.LightService
import no.lundedev.core.service.UpdateLightCommand
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val lightService: LightService
) {

    @GetMapping("/lights")
    fun getLights(): List<LightDto> {
        return lightService.getLights()
    }

    @PostMapping("/lights/{id}/toggle")
    fun toggleLight(@PathVariable id: String) {
        lightService.toggleLight(id)
    }

    @PostMapping("/lights/{id}/state")
    fun updateLight(@PathVariable id: String, @RequestBody cmd: UpdateLightCommand) {
        lightService.updateLight(id, cmd)
    }
}
