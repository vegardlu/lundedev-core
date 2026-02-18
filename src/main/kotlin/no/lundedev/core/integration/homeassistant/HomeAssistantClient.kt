package no.lundedev.core.integration.homeassistant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class HomeAssistantClient(
    builder: RestClient.Builder,
    private val properties: HomeAssistantProperties
) {
    private val logger = LoggerFactory.getLogger(HomeAssistantClient::class.java)
    private val client: RestClient = builder
        .baseUrl(properties.url)
        .defaultHeader("Authorization", "Bearer ${properties.token}")
        .defaultHeader("Content-Type", "application/json")
        .build()

    fun getStates(): List<EntityState> {
        logger.info("Fetching states from Home Assistant at {}", properties.url)
        return try {
            client.get()
                .uri("/api/states")
                .retrieve()
                .body<Array<EntityState>>()
                ?.toList()
                ?.also { 
                    logger.info("Fetched ${it.size} entities. First 5 inputs: ${it.take(5).map { e -> "${e.entity_id}=${e.state}" }}") 
                }
                ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to fetch states from Home Assistant", e)
            emptyList()
        }
    }

    fun getAreas(): List<String> {
        val template = """
            {{ areas() | to_json }}
        """.trimIndent()
        
        val rendered = renderTemplate(template)
        if (rendered.isBlank()) return emptyList()
        
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            mapper.readValue(rendered, List::class.java) as List<String>
        } catch (e: Exception) {
            logger.error("Failed to parse areas from Home Assistant", e)
            emptyList()
        }
    }

    fun getEntitiesWithArea(domain: String? = null, area: String? = null): List<EnhancedEntityState> {
        val domainFilter = if (domain != null) "if state.entity_id.startswith('$domain.')" else ""
        // Match area name or ID case-insensitively
        val areaFilter = if (area != null) {
            val lowerArea = area.lowercase()
            "if (area_name(state.entity_id) or '').lower() == '$lowerArea' or (area_id(state.entity_id) or '').lower() == '$lowerArea'"
        } else ""
        
        // Combine filters. If both exist, we need 'and'.
        val filters = listOf(domainFilter, areaFilter).filter { it.isNotEmpty() }.joinToString(" and ")
        val filterString = if (filters.isNotEmpty()) filters else ""

        val template = """
            [
            {%- for state in states $filterString -%}
            {
              "entity_id": {{ state.entity_id | to_json }},
              "friendly_name": {{ state.name | to_json }},
              "area_id": {{ area_id(state.entity_id) | to_json }},
              "area": {{ area_name(state.entity_id) | to_json }},
              "floor": {{ floor_name(state.entity_id) | to_json }},
              "state": {{ state.state | to_json }},
              "attributes": {{ state.attributes | to_json }}
            }{% if not loop.last %},{% endif %}
            {%- endfor -%}
            ]
        """.trimIndent()

        val rendered = renderTemplate(template)
        
        // Helper function for fallback
        fun fallback(): List<EnhancedEntityState> {
            logger.warn("Template rendering failed or returned empty. Falling back to /api/states.")
            return getStates().map { state ->
                EnhancedEntityState(
                    entity_id = state.entity_id,
                    friendly_name = (state.attributes["friendly_name"] as? String) ?: state.entity_id,
                    area_id = null,
                    area = null,
                    floor = null,
                    state = state.state,
                    attributes = state.attributes
                )
            }.filter { entity ->
                // Fallback: match domain only, as area info is missing
                val matchDomain = domain == null || entity.entity_id.startsWith("$domain.")
                matchDomain
            }
        }

        if (rendered.isBlank()) return fallback()

        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any?>>>() {}
            val rawList = mapper.readValue(rendered, typeRef)
            
            if (rawList.isEmpty() && (domain == null && area == null)) {
                // If we asked for everything and got nothing, something is wrong.
                return fallback()
            }
            
            rawList.mapNotNull { data ->
                val entityId = data["entity_id"] as? String
                if (entityId != null) {
                    EnhancedEntityState(
                        entity_id = entityId,
                        friendly_name = (data["friendly_name"] as? String) ?: entityId,
                        area_id = (data["area_id"] as? String)?.takeIf { it != "None" && it != "unknown" },
                        area = (data["area"] as? String)?.takeIf { it != "None" && it != "unknown" },
                        floor = (data["floor"] as? String)?.takeIf { it != "None" && it != "unknown" },
                        state = (data["state"] as? String) ?: "unknown",
                        attributes = (data["attributes"] as? Map<String, Any?>) ?: emptyMap()
                    )
                } else null
            }
        } catch (e: Exception) {
            logger.error("Failed to parse entities JSON from Home Assistant: ${e.message}. Falling back.", e)
            fallback()
        }
    }

    fun renderTemplate(template: String): String {
        logger.debug("Rendering template in Home Assistant: {}", template)
        return try {
            client.post()
                .uri("/api/template")
                .body(mapOf("template" to template))
                .retrieve()
                .body<String>()
                ?: ""
        } catch (e: Exception) {
            logger.error("Failed to render template in Home Assistant", e)
            ""
        }
    }

    fun callService(domain: String, service: String, entityId: String, data: Map<String, Any> = emptyMap()) {
        logger.info("Calling service {}.{} for entity {}", domain, service, entityId)
        try {
            val payload = mapOf("entity_id" to entityId) + data
            client.post()
                .uri("/api/services/$domain/$service")
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            logger.error("Failed to call service {}.{} for entity {}", domain, service, entityId, e)
            throw e
        }
    }
}

data class EntityState(
    val entity_id: String,
    val state: String,
    val attributes: Map<String, Any?> = emptyMap()
)

data class EnhancedEntityState(
    val entity_id: String,
    val friendly_name: String,
    val area_id: String?, // area_id()
    val area: String?,    // area_name()
    val floor: String?,
    val state: String,
    val attributes: Map<String, Any?>
)
