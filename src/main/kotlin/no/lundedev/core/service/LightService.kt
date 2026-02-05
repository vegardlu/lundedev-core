package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class LightService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun getLights(): List<LightDto> {
        return homeAssistantClient.getStates()
            .filter { it.entity_id.startsWith("light.") }
            .map { entity ->
                val friendlyName = entity.attributes["friendly_name"] as? String ?: entity.entity_id
                // Use is_hue_group to filter out groups if needed, or keeping them for now.
                // We can refine filtering later.
                LightDto(
                    id = entity.entity_id,
                    name = friendlyName,
                    isOn = entity.state == "on",
                    brightness = (entity.attributes["brightness"] as? Number)?.toInt()
                )
            }
    }
    fun toggleLight(id: String) {
        homeAssistantClient.callService("light", "toggle", id)
    }

    fun updateLight(id: String, cmd: UpdateLightCommand) {
        val data = mutableMapOf<String, Any>()
        if (cmd.isOn == true) {
            // If explicit on/off is requested
            homeAssistantClient.callService("light", "turn_on", id)
        } else if (cmd.isOn == false) {
             homeAssistantClient.callService("light", "turn_off", id)
             return // If turning off, ignore other parameters
        }

        // If just adjusting params (which implies ON)
        cmd.brightness?.let { data["brightness"] = it }
        // Simple color support assuming RGB list for now
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
    val isOn: Boolean,
    val brightness: Int? = null
)
