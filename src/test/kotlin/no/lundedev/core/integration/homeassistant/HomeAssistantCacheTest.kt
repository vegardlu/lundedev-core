package no.lundedev.core.integration.homeassistant

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeAssistantCacheTest {

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
}
