package no.lundedev.core.integration.homeassistant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import jakarta.annotation.PostConstruct

@Service
class HomeAssistantCache(
    private val client: HomeAssistantClient
) {
    private val logger = LoggerFactory.getLogger(HomeAssistantCache::class.java)

    // Cache for entities: Map<EntityID, EnhancedEntityState>
    private val entityCache = ConcurrentHashMap<String, EnhancedEntityState>()
    
    // Cache for specific entity lookups if needed, but the map above covers it
    
    // Cache for areas: List<String> (Area Names)
    // We might want to map AreaID -> AreaName, but for now a simple list of names and 
    // ensuring entities have area names attached is the strategy. 
    // However, `list_areas` currently returns area IDs from the jinja template `areas()`. 
    // We should probably cache what the client returns.
    private val areaCache = AtomicReference<List<String>>(emptyList())

    @PostConstruct
    fun init() {
        logger.info("Initializing HomeAssistantCache...")
        refreshCache()
    }

    @Scheduled(fixedRate = 5000) // Poll every 5 seconds
    fun refreshCache() {
        try {
            updateAreas()
            updateEntities()
        } catch (e: Exception) {
            logger.error("Failed to refresh Home Assistant cache", e)
        }
    }

    private fun updateAreas() {
        val areas = client.getAreas() // Client currently returns list of IDs/Names from template
        areaCache.set(areas)
    }

    private fun updateEntities() {
        // We need a way to get *all* entities with their enhanced attributes (area, floor, etc.)
        // The client currently has `getEntitiesWithArea` which uses a template.
        // We should add a method to get *all* enhanced entities efficiently.
        // For now, let's assume we use the same template but without filters to get everything.
        
        val entities = client.getEntitiesWithArea(domain = null, area = null)
        if (entities.isNotEmpty()) {
            val newMap = entities.associateBy { it.entity_id }
            entityCache.putAll(newMap)
            // Remove stale entries? 
            // Better to replace the whole map if we want to handle deletions, 
            // but `ConcurrentHashMap` doesn't have `setAll`. 
            // Let's iterate and remove keys not in newMap if we want strict consistency,
            // or just use clear+putAll (atomic reference would be better for full swap).
            
            // For thread safety during reads, let's use the AtomicReference approach for the whole map 
            // or just rely on the fact that eventual consistency is fine.
            // Actually, let's verify if `getEntitiesWithArea` returns everything.
            // Yes, calling it with nulls uses a template that iterates `states`.
        }
    }
    
    // --- Access Methods ---

    fun getAreas(): List<String> {
        return areaCache.get()
    }

    fun getAllEntities(): List<EnhancedEntityState> {
        return entityCache.values.toList()
    }
    
    fun getEntity(entityId: String): EnhancedEntityState? {
        return entityCache[entityId]
    }
    
    /**
     * Filter cached entities in memory. 
     * This replaces the server-side filtering logic in the client for chat purposes.
     */
    fun getEntities(domain: String? = null, area: String? = null): List<EnhancedEntityState> {
        val all = getAllEntities()
        
        return all.filter { entity ->
            val matchDomain = domain == null || entity.entity_id.startsWith("$domain.")
            
            val matchArea = if (area == null) {
                true
            } else {
                val lowerArea = area.lowercase().replace("_", " ")
                val entityAreaName = entity.area?.lowercase()?.replace("_", " ")
                val entityAreaId = entity.area_id?.lowercase()?.replace("_", " ")
                
                // Match if EITHER area_name OR area_id matches the query
                (entityAreaName == lowerArea) || (entityAreaId == lowerArea)
            }
            
            matchDomain && matchArea
        }
    }
    /**
     * Smart search with scoring and synonyms.
     * Supports multi-term queries (e.g., "stua lys") and synonyms.
     */
    fun search(query: String): List<EnhancedEntityState> {
        val normalizedQuery = query.lowercase().replace("_", " ").trim()
        if (normalizedQuery.isBlank()) return emptyList()

        // 1. Tokenize & Expand query with synonyms
        val searchTerms = expandSynonyms(normalizedQuery)

        // 2. Score all entities
        val scoredEntities = getAllEntities().map { entity ->
            val score = calculateScore(entity, searchTerms, normalizedQuery)
            entity to score
        }

        // 3. Filter and Sort
        return scoredEntities
            .filter { it.second > 0 }
            .sortedByDescending { it.second } // Highest score first
            .take(15) // Return top 15 matches
            .map { it.first }
    }

    private fun expandSynonyms(query: String): Set<String> {
        // Tokenize the input string by spaces to handle multi-word queries
        val rawTerms = query.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val terms = rawTerms.toMutableSet()
        
        // Simple manual map remains helpful as a base, but the AI is expected to provide translations too.
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

        mappings.forEach { (norwegian, englishList) ->
            // Check if any token matches the Norwegian key
            if (rawTerms.any { it == norwegian }) {
                terms.addAll(englishList)
            }
        }
        return terms
    }

    private fun calculateScore(entity: EnhancedEntityState, searchTerms: Set<String>, originalQuery: String): Int {
        var score = 0
        val id = entity.entity_id.lowercase().replace("_", " ")
        val name = entity.friendly_name.lowercase()
        val areaId = entity.area_id?.lowercase()?.replace("_", " ") ?: ""
        val areaName = entity.area?.lowercase() ?: ""

        // Exact ID match is golden (e.g., user asks for specific ID)
        if (id == originalQuery) score += 200
        if (name == originalQuery) score += 150
        
        // Check against all expanded terms (synonyms & tokens)
        for (term in searchTerms) {
            // Area matching is extremely high value for context ("lights in living room")
            if (areaId == term || areaName == term) score += 80
            else if (areaId.contains(term) || areaName.contains(term)) score += 40

            // Entity Name/ID matching
            if (id.contains(term)) score += 50
            if (name.contains(term)) score += 50
            
            // Domain specific boost if user asks for generic types
            // e.g. term="light" and entity is "light.living_room" -> boost
            if (entity.entity_id.startsWith(term)) score += 20
        }

        return score
    }


}
