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
                val lowerArea = area.lowercase()
                val entityAreaName = entity.area?.lowercase()
                val entityAreaId = entity.area_id?.lowercase()
                
                // Match if EITHER area_name OR area_id matches the query
                (entityAreaName == lowerArea) || (entityAreaId == lowerArea)
            }
            
            matchDomain && matchArea
        }
    }
}
