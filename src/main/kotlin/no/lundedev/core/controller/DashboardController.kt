package no.lundedev.core.controller

import no.lundedev.core.service.LightDto
import no.lundedev.core.service.LightService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
    private val lightService: LightService
) {

    @GetMapping("/lights")
    fun getLights(): List<LightDto> {
        return lightService.getLights()
    }
}
