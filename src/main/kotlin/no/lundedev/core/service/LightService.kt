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
        return homeAssistantClient.getStates()
            .filter { it.entity_id.startsWith("light.") }
            .map { entity ->
                val friendlyName = entity.attributes["friendly_name"] as? String ?: entity.entity_id
                val isOn = entity.state.equals("on", ignoreCase = true)
                
                logger.debug("Light {} -> state='{}', parsedIsOn={}", entity.entity_id, entity.state, isOn)
                
                LightDto(
                    id = entity.entity_id,
                    name = friendlyName,
                    isOn = isOn,
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
    val brightness: Int? = null
)
