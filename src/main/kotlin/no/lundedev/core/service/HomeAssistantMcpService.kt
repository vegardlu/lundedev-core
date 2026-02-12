package no.lundedev.core.service

import no.lundedev.core.integration.homeassistant.HomeAssistantClient
import org.springframework.stereotype.Service

@Service
class HomeAssistantMcpService(
    private val homeAssistantClient: HomeAssistantClient
) {
    fun listEntities(domain: String? = null): List<String> {
        val template = """
            {% for state in states ${if (domain != null) "if state.entity_id.startswith('$domain.')" else ""} -%}
            {{ state.entity_id }}|{{ state.name }}|{{ area_name(state.entity_id) }}|{{ floor_name(state.entity_id) }}|{{ state.state }}
            {% endfor %}
        """.trimIndent()

        val rendered = homeAssistantClient.renderTemplate(template)
        
        if (rendered.isBlank()) {
            // Fallback to basic list if template rendering fails or returns empty
            return homeAssistantClient.getStates()
                .filter { domain == null || it.entity_id.startsWith("$domain.") }
                .map { it.entity_id }
        }

        return rendered.lines().filter { it.isNotBlank() }
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
