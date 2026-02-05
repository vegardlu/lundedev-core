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
                ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to fetch states from Home Assistant", e)
            emptyList()
        }
    }
}

data class EntityState(
    val entity_id: String,
    val state: String,
    val attributes: Map<String, Any?> = emptyMap()
)
