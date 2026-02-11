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
