package no.lundedev.core.service

import com.fasterxml.jackson.annotation.JsonProperty
import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class LightService(
    private val homeAssistantClient: HomeAssistantClient
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(LightService::class.java)

    fun getLights(): List<LightDto> {
        // Use a template to get all details in one request, including area and floor which aren't in standard state attributes
        val template = """
            {% for state in states.light -%}
            {{ state.entity_id }}|{{ state.name }}|{{ area_name(state.entity_id) }}|{{ floor_name(state.entity_id) }}|{{ state.state }}|{{ state.attributes.brightness }}
            {% endfor %}
        """.trimIndent()

        val rendered = homeAssistantClient.renderTemplate(template)
        
        if (rendered.isBlank()) return emptyList()

        return rendered.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val parts = line.split("|")
                    if (parts.size >= 5) {
                        val id = parts[0]
                        val name = parts[1]
                        val area = parts[2].takeIf { it != "None" && it != "unknown" }
                        val floor = parts[3].takeIf { it != "None" && it != "unknown" }
                        val state = parts[4]
                        val brightness = parts.getOrNull(5)?.takeIf { it != "None" && it != "unknown" }?.toIntOrNull()
                        val isOn = state.equals("on", ignoreCase = true)

                        LightDto(id, name, isOn, brightness, area, floor)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Failed to parse light line: {}", line, e)
                    null
                }
            }
    }
    fun toggleLight(id: String) {
        homeAssistantClient.callService("light", "toggle", id)
    }

    fun updateLight(id: String, cmd: UpdateLightCommand) {
        val data = mutableMapOf<String, Any>()
        if (cmd.isOn == true) {
            homeAssistantClient.callService("light", "turn_on", id)
        } else if (cmd.isOn == false) {
             homeAssistantClient.callService("light", "turn_off", id)
             return
        }

        // If just adjusting params (which implies ON)
        cmd.brightness?.let { data["brightness"] = it }
        cmd.color?.let { data["rgb_color"] = it }

        if (data.isNotEmpty()) {
             homeAssistantClient.callService("light", "turn_on", id, data)
        }
    }
}

data class UpdateLightCommand(
    val isOn: Boolean? = null,
    val brightness: Int? = null,
    val color: List<Int>? = null // [r, g, b]
)

data class LightDto(
    val id: String,
    val name: String,
    @JsonProperty("isOn")
    val isOn: Boolean,
    val brightness: Int? = null,
    val area: String? = null,
    val floor: String? = null
)
