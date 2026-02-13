package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class HomeAssistantMcpService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun listEntities(domain: String? = null, area: String? = null): List<String> {
        return homeAssistantClient.getEntitiesWithArea(domain, area)
            .map { entity ->
                val deviceClass = entity.attributes["device_class"] ?: ""
                val unit = entity.attributes["unit_of_measurement"] ?: ""
                "${entity.entity_id}|${entity.friendly_name}|${entity.area ?: "None"}|${entity.floor ?: "None"}|${entity.state}|$deviceClass|$unit"
            }
    }

    fun listAreas(): List<String> {
        return homeAssistantClient.getAreas()
    }

    fun getState(entityId: String): Map<String, Any?>? {
        val state = homeAssistantClient.getStates().find { it.entity_id == entityId }
        return state?.let {
            mapOf(
                "state" to it.state,
                "attributes" to it.attributes
            )
        }
    }

    fun callService(domain: String, service: String, entityId: String, payload: Map<String, Any> = emptyMap()) {
        homeAssistantClient.callService(domain, service, entityId, payload)
    }
}
