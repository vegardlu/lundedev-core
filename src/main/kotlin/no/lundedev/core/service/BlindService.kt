package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class BlindService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun getBlinds(): List<BlindDto> {
        return homeAssistantClient.getEntitiesWithArea("cover")
            .map { entity ->
                BlindDto(
                    id = entity.entity_id,
                    name = entity.friendly_name,
                    state = entity.state,
                    currentPosition = (entity.attributes["current_position"] as? Number)?.toInt(),
                    area = entity.area,
                    floor = entity.floor
                )
            }
    }

    fun setPosition(id: String, position: Int) {
        homeAssistantClient.callService("cover", "set_cover_position", id, mapOf("position" to position))
    }

    fun open(id: String) {
        homeAssistantClient.callService("cover", "open_cover", id)
    }

    fun close(id: String) {
        homeAssistantClient.callService("cover", "close_cover", id)
    }

    fun stop(id: String) {
        homeAssistantClient.callService("cover", "stop_cover", id)
    }
}

data class BlindDto(
    val id: String,
    val name: String,
    val state: String,
    val currentPosition: Int?,
    val area: String?,
    val floor: String?
)
