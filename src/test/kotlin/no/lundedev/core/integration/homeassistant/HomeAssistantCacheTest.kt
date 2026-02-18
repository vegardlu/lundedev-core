package no.lundedev.core.integration.homeassistant

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeAssistantCacheTest {
// ... existing code ...





    private lateinit var client: HomeAssistantClient
    private lateinit var cache: HomeAssistantCache

    @BeforeEach
    fun setUp() {
        client = mockk(relaxed = true)
        cache = HomeAssistantCache(client)
    }

    @Test
    fun `refreshCache should update entities`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap()),
            EnhancedEntityState("switch.living", "Living Switch", "living_room", "Living Room", "First Floor", "off", emptyMap())
        )
        every { client.getEntitiesWithArea(null, null) } returns entities
        every { client.getAreas() } returns listOf("Kitchen", "Living Room")

        cache.refreshCache()

        assertEquals(2, cache.getAllEntities().size)
        assertEquals("Kitchen Light", cache.getEntity("light.kitchen")?.friendly_name)
    }

    @Test
    fun `getEntities should filter by domain`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap()),
            EnhancedEntityState("switch.kitchen", "Kitchen Switch", "kitchen", "Kitchen", "First Floor", "off", emptyMap())
        )
        every { client.getEntitiesWithArea(null, null) } returns entities
        cache.refreshCache()

        val lights = cache.getEntities(domain = "light")
        assertEquals(1, lights.size)
        assertEquals("light.kitchen", lights[0].entity_id)
    }

    @Test
    fun `getEntities should filter by area name`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap()),
            EnhancedEntityState("light.living", "Living Light", "living_room", "Living Room", "First Floor", "on", emptyMap())
        )
        every { client.getEntitiesWithArea(null, null) } returns entities
        cache.refreshCache()

        val results = cache.getEntities(area = "Kitchen")
        assertEquals(1, results.size)
        assertEquals("light.kitchen", results[0].entity_id)
    }
    
    @Test
    fun `getEntities should filter by area id`() {
        val entities = listOf(
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap()),
            EnhancedEntityState("light.living", "Living Light", "living_room", "Living Room", "First Floor", "on", emptyMap())
        )
        every { client.getEntitiesWithArea(null, null) } returns entities
        cache.refreshCache()

        // Searching by ID "living_room" should match "Living Room" entity because of area_id match
        val results = cache.getEntities(area = "living_room")
        assertEquals(1, results.size)
        assertEquals("light.living", results[0].entity_id)
    }

    @Test
    fun `getEntities should match area id with spaces`() {
        val entities = listOf(
            EnhancedEntityState("light.living", "Living Light", "living_room", "Living Room", "First Floor", "on", emptyMap())
        )
        every { client.getEntitiesWithArea(null, null) } returns entities
        cache.refreshCache()

        // Searching for "living room" should match area_id "living_room" due to normalization
        val results = cache.getEntities(area = "living room")
        assertEquals(1, results.size)
        assertEquals("light.living", results[0].entity_id)
    }

    @Test
    fun `search should handle synonyms and scoring`() {
        val entities = listOf(
            EnhancedEntityState("light.living_room_ceiling", "Living Ceiling", "living_room", "Living Room", "First Floor", "on", emptyMap()),
            EnhancedEntityState("switch.living_room_wall", "Living Wall", "living_room", "Living Room", "First Floor", "off", emptyMap()),
            EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Ki", "First Floor", "on", emptyMap())
        )
        every { client.getEntitiesWithArea(null, null) } returns entities
        cache.refreshCache()

        // 1. Search for "stua" (Norwegian) should find living room entities
        val stuaResults = cache.search("stua")
        assertEquals(2, stuaResults.size)
        assertTrue(stuaResults.any { it.entity_id == "light.living_room_ceiling" })
        
        // 2. Search for "lights stua" should prioritize lights but include the switch if relevant
        val lightResults = cache.search("stua lys")
        assertTrue(lightResults.size >= 2) // Might include other lights due to "lys"->"light" mapping
        
        // Ideally checking order, but score might be close. 
        // light.living_room_ceiling starts with "light", so it gets +20 boost from domain logic.
        // It also matches "living room" (Area) -> High Score.
        // light.kitchen only matches "light" -> Lower Score.
        assertEquals("light.living_room_ceiling", lightResults[0].entity_id)
    }
    @Test
    fun `refreshCache should handle client failure gracefully`() {
        // Initial success
        every { client.getEntitiesWithArea(null, null) } returns listOf(
             EnhancedEntityState("light.kitchen", "Kitchen Light", "kitchen", "Kitchen", "First Floor", "on", emptyMap())
        )
        // Set area cache too
        every { client.getAreas() } returns listOf("Kitchen")
        
        cache.refreshCache()
        assertEquals(1, cache.getAllEntities().size)
        
        // Second call fails
        every { client.getEntitiesWithArea(null, null) } throws RuntimeException("Connection failed")
        
        cache.refreshCache()
        
        // Cache should remain populated
        assertEquals(1, cache.getAllEntities().size)
        assertEquals("light.kitchen", cache.getEntity("light.kitchen")?.entity_id)
    }
}
