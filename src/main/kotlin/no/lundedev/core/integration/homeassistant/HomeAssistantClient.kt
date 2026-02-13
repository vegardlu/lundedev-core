package no.lundedev.core.integration.homeassistant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class HomeAssistantClient(
    builder: RestClient.Builder,
    private val properties: HomeAssistantProperties
) {
    private val logger = LoggerFactory.getLogger(HomeAssistantClient::class.java)
    private val client: RestClient

    init {
        this.client = builder
            .baseUrl(properties.url)
            .defaultHeader("Authorization", "Bearer ${properties.token}")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }

    fun getStates(): List<EntityState> {
        logger.info("Fetching states from Home Assistant at {}", properties.url)
        return try {
            client.get()
                .uri("/api/states")
                .retrieve()
                .body(Array<EntityState>::class.java)
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
        // Logic: case-insensitive match for area name. convert both to lower() in jinja.
        // We use `area_name(state.entity_id)` and handle if it is None by defaulting to empty string before lower()
        val areaFilter = if (area != null) "if (area_name(state.entity_id) or '').lower() == '${area.lowercase()}'" else ""
        
        // Combine filters. If both exist, we need 'and'.
        val filters = listOf(domainFilter, areaFilter).filter { it.isNotEmpty() }.joinToString(" and ")
        val filterString = if (filters.isNotEmpty()) filters else ""

        val template = """
            [
            {%- for state in states $filterString -%}
            {
              "entity_id": {{ state.entity_id | to_json }},
              "friendly_name": {{ state.name | to_json }},
              "area": {{ area_name(state.entity_id) | to_json }},
              "floor": {{ floor_name(state.entity_id) | to_json }},
              "state": {{ state.state | to_json }},
              "attributes": {{ state.attributes | to_json }}
            }{% if not loop.last %},{% endif %}
            {%- endfor -%}
            ]
        """.trimIndent()

        val rendered = renderTemplate(template)
        
        if (rendered.isBlank()) return emptyList()

        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val typeRef = object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any?>>>() {}
            val rawList = mapper.readValue(rendered, typeRef)
            
            rawList.mapNotNull { data ->
                val entityId = data["entity_id"] as? String
                if (entityId != null) {
                    EnhancedEntityState(
                        entity_id = entityId,
                        friendly_name = (data["friendly_name"] as? String) ?: entityId,
                        area = (data["area"] as? String)?.takeIf { it != "None" && it != "unknown" },
                        floor = (data["floor"] as? String)?.takeIf { it != "None" && it != "unknown" },
                        state = (data["state"] as? String) ?: "unknown",
                        attributes = (data["attributes"] as? Map<String, Any?>) ?: emptyMap()
                    )
                } else null
            }
        } catch (e: Exception) {
            logger.error("Failed to parse entities JSON from Home Assistant", e)
            emptyList()
        }
    }

    fun renderTemplate(template: String): String {
        logger.debug("Rendering template in Home Assistant: {}", template)
        return try {
            client.post()
                .uri("/api/template")
                .body(mapOf("template" to template))
                .retrieve()
                .body(String::class.java)
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
    val area: String?,
    val floor: String?,
    val state: String,
    val attributes: Map<String, Any?>
)
