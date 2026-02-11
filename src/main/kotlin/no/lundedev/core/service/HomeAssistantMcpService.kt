package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class HomeAssistantMcpService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun listEntities(domain: String? = null): List<String> {
        return homeAssistantClient.getStates()
            .filter { domain == null || it.entity_id.startsWith("$domain.") }
            .map { it.entity_id }
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
