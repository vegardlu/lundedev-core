package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import no.lundedev.core.integration.homeassistant.HomeAssistantCache
import no.lundedev.core.integration.homeassistant.EnhancedEntityState
import org.springframework.stereotype.Service

@Service
class HomeAssistantMcpService(
    private val homeAssistantClient: HomeAssistantClient,
    private val homeAssistantCache: HomeAssistantCache
) {
    fun listEntities(domain: String? = null, area: String? = null): List<String> {
        return homeAssistantCache.getEntities(domain, area)
            .map { entity ->
                val deviceClass = entity.attributes["device_class"] ?: ""
                val unit = entity.attributes["unit_of_measurement"] ?: ""
                "${entity.entity_id}|${entity.friendly_name}|${entity.area ?: "None"}|${entity.floor ?: "None"}|${entity.state}|$deviceClass|$unit"
            }
    }

    fun listAreas(): List<String> {
        return homeAssistantCache.getAreas()
    }

    fun getState(entityId: String): Map<String, Any?>? {
        val state = homeAssistantCache.getEntity(entityId)
        return state?.let {
            mapOf(
                "state" to it.state,
                "attributes" to it.attributes
            )
        }
    }

    fun callService(domain: String, service: String, entityId: String, payload: Map<String, Any> = emptyMap()) {
        homeAssistantClient.callService(domain, service, entityId, payload)
        // Ideally, we should invalidate/update cache for this entity immediately, 
        // but the poller will catch it in 5s. For instant feedback, we might want to manually update cache here later.
    }
}
