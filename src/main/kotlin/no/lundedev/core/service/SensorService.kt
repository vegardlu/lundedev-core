package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class SensorService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun getSensors(): List<SensorDto> {
        return homeAssistantClient.getEntitiesWithArea("sensor")
            .map { entity ->
                SensorDto(
                    id = entity.entity_id,
                    name = entity.friendly_name,
                    state = entity.state,
                    unitOfMeasurement = entity.attributes["unit_of_measurement"] as? String,
                    deviceClass = entity.attributes["device_class"] as? String,
                    area = entity.area,
                    floor = entity.floor
                )
            }
    }
}

data class SensorDto(
    val id: String,
    val name: String,
    val state: String,
    val unitOfMeasurement: String?,
    val deviceClass: String?,
    val area: String?,
    val floor: String?
)
