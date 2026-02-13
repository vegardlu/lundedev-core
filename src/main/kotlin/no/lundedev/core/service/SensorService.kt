package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class SensorService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun getSensors(): List<SensorDto> {
        return homeAssistantClient.getEntitiesWithArea("sensor")
            .filter { entity ->
                val deviceClass = entity.attributes["device_class"] as? String
                val name = entity.friendly_name
                val state = entity.state

                // Filter out timestamp/date sensors
                if (deviceClass == "timestamp" || deviceClass == "date") return@filter false
                
                // Filter out typically noisy mobile app sensors
                if (name.contains("iPhone", ignoreCase = true) || 
                    name.contains("Phone", ignoreCase = true) || 
                    name.contains("Pixel", ignoreCase = true)) return@filter false

                // Filter out backup manager
                if (entity.entity_id.contains("backup", ignoreCase = true)) return@filter false

                // Filter out unavailable/unknown states
                if (state == "unavailable" || state == "unknown") return@filter false

                // Filter out obvious timestamps in state (ISO8601-ish start)
                if (state.matches(Regex("^\\d{4}-\\d{2}-\\d{2}.*"))) return@filter false

                true
            }
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
