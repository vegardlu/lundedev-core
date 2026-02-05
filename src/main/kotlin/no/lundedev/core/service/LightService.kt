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
}

data class LightDto(
    val id: String,
    val name: String,
    val isOn: Boolean,
    val brightness: Int? = null
)
