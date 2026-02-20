package no.lundedev.core.integration.homeassistant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference
import jakarta.annotation.PostConstruct

@Service
class HomeAssistantCache(
    private val client: HomeAssistantClient
) {
    private val logger = LoggerFactory.getLogger(HomeAssistantCache::class.java)

    private val entityCache = AtomicReference<Map<String, EnhancedEntityState>>(emptyMap())
    private val areaCache = AtomicReference<List<String>>(emptyList())

    @PostConstruct
    fun init() {
        logger.info("Initializing HomeAssistantCache...")
        refreshCache()
    }

    @Scheduled(fixedRate = 5000)
    fun refreshCache() {
        try {
            updateAreas()
            updateEntities()
        } catch (e: Exception) {
            logger.error("Failed to refresh Home Assistant cache", e)
        }
    }

    private fun updateAreas() {
        areaCache.set(client.getAreas())
    }

    private fun updateEntities() {
        val entities = client.getEntitiesWithArea(domain = null, area = null)
        if (entities.isNotEmpty()) {
            entityCache.set(entities.associateBy { it.entity_id })
        }
    }

    fun getAreas(): List<String> {
        return areaCache.get()
    }

    fun getAllEntities(): List<EnhancedEntityState> {
        return entityCache.get().values.toList()
    }
    
    fun getEntity(entityId: String): EnhancedEntityState? {
        return entityCache.get()[entityId]
    }
    
    fun getEntities(domain: String? = null, area: String? = null): List<EnhancedEntityState> {
        val lowerArea = area?.lowercase()?.replace("_", " ")
        
        return getAllEntities().filter { entity ->
            val matchDomain = domain == null || entity.entity_id.startsWith("$domain.")
            val matchArea = lowerArea == null || 
                entity.area?.lowercase()?.replace("_", " ") == lowerArea || 
                entity.area_id?.lowercase()?.replace("_", " ") == lowerArea
            
            matchDomain && matchArea
        }
    }

    fun search(query: String): List<EnhancedEntityState> {
        val normalizedQuery = query.lowercase().replace("_", " ").trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val searchTerms = expandSynonyms(normalizedQuery)

        return getAllEntities()
            .map { entity -> entity to calculateScore(entity, searchTerms, normalizedQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(15)
            .map { it.first }
    }

    private fun expandSynonyms(query: String): Set<String> {
        val rawTerms = query.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        val mappings = mapOf(
            "stua" to listOf("living room", "living"),
            "kjøkken" to listOf("kitchen"),
            "soverom" to listOf("bedroom"),
            "bad" to listOf("bathroom"),
            "gang" to listOf("hallway", "entrance"),
            "lys" to listOf("light", "switch", "dimmer"),
            "varme" to listOf("climate", "thermostat", "temperature"),
            "gardiner" to listOf("cover", "blind")
        )

        val expandedTerms = rawTerms.flatMap { term ->
            mappings[term] ?: emptyList()
        }

        return (rawTerms + expandedTerms).toSet()
    }

    private fun calculateScore(entity: EnhancedEntityState, searchTerms: Set<String>, originalQuery: String): Int {
        val id = entity.entity_id.lowercase().replace("_", " ")
        val name = entity.friendly_name.lowercase()
        val areaId = entity.area_id?.lowercase()?.replace("_", " ") ?: ""
        val areaName = entity.area?.lowercase() ?: ""

        val exactMatchScore = (if (id == originalQuery) 200 else 0) +
            (if (name == originalQuery) 150 else 0)
        
        val termsScore = searchTerms.sumOf { term ->
            val areaScore = when {
                areaId == term || areaName == term -> 80
                areaId.contains(term) || areaName.contains(term) -> 40
                else -> 0
            }

            val idScore = if (id.contains(term)) 50 else 0
            val nameScore = if (name.contains(term)) 50 else 0
            val domainScore = if (entity.entity_id.startsWith(term)) 20 else 0
            
            areaScore + idScore + nameScore + domainScore
        }

        return exactMatchScore + termsScore
    }
}
