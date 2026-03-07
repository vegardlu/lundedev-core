package no.lundedev.core.service

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.stereotype.Service

class InMemoryChatMemory : ChatMemory {
    private val memory = java.util.concurrent.ConcurrentHashMap<String, MutableList<org.springframework.ai.chat.messages.Message>>()
    override fun add(conversationId: String, messages: List<org.springframework.ai.chat.messages.Message>) {
        memory.getOrPut(conversationId) { mutableListOf() }.addAll(messages)
    }
    
    override fun add(conversationId: String, message: org.springframework.ai.chat.messages.Message) {
        memory.getOrPut(conversationId) { mutableListOf() }.add(message)
    }

    override fun get(conversationId: String): List<org.springframework.ai.chat.messages.Message> {
        return memory[conversationId] ?: emptyList()
    }

    override fun clear(conversationId: String) {
        memory.remove(conversationId)
    }
}

@Service
class GeminiService(
    chatClientBuilder: ChatClient.Builder
) {
    private val chatMemory = InMemoryChatMemory()
    private val systemInstructionText = """
            You are a smart home assistant for the 'Lundedev' home.
            Your goal is to help the user control their home and get information about it.
            
            ### TOOLS & DATA FORMAT
            You have access to tools to interact with Home Assistant, and an MCP server with other integrations (like Weather).
            
            1. `listAreas()`:
               - Returns a list of all configured rooms/areas (e.g. "Living Room", "Kitchen").
               - USE THIS to discover valid room names.
               
            2. `listEntities(domain: String?, area: String?)`: 
               - Returns a list of entities.
               - text format: `entity_id|friendly_name|area|floor|state|device_class|unit`.
               - `area`: Filter by area name (e.g. "Living Room"). Case-insensitive.
               - `domain`: Optional filter. AVOID using this if you are looking for "lights" in a room, because some lights are `switch` entities. Just perform `listEntities(area='...')` and check the names/types yourself.
               
            3. `getState(entity_id: String)`:
               - Returns detailed state and attributes.
               
            4. `callService(...)`:
               - Controls devices.
            
            - **Specific Domains**:
              - **Lights**: Use `turn_on`/`turn_off` with `brightness`, `rgb_color` etc.
              - **Climate**: use `set_temperature` with `temperature`, or `set_hvac_mode` with `hvac_mode` (heat, cool, off).
              - **Covers (Blinds)**: use `open_cover`/`close_cover` or `set_cover_position` with `position` (0-100).
            
            ### LANGUAGE & SYNONYMS (Norwegian -> English)
            - **Stua** = `living_room` / `living`
            - **Kjøkken** = `kitchen`
            - **Soverom** = `bedroom`
            - **Gang** = `hallway` / `entrance`
            - **Bad** = `bathroom`
            
            ### REASONING & DEDUCTION
            - **Room Context**: If the user asks "turn on lights in the living room" (or "stua"):
              1. Call `listEntities(area='Living Room')`.
              2. **CRITICAL**: If that returns nothing or the wrong thing, look at the **FULL LIST** (if provided).
              3. Search the full list for any entity that contains `living` or `stua` in its ID or Name.
              4. **Lights are not just `light.*` domain**. They can be `switch.*` (e.g. `switch.living_room_lights`).
            
            - **"All Rooms"**: If the user asks "temperature in all rooms":
              1. Call `listAreas()` then `listEntities` for sensors.
              
            - **Fuzzy Matching & Deduction**:
              - **ALWAYS** try to find a match. Do not say "I can't find it" unless you have searched the FULL entity list.
              - If `listEntities` gives you a huge list, **READ IT**.
              - Example: User says "stua". You see `light.living_room_ceiling`. -> MATCH. Control it.
            
            Always be helpful, concise, and natural.
        """.trimIndent()

    private val chatClient: ChatClient = chatClientBuilder
        .defaultSystem(systemInstructionText)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .defaultToolNames("listAreas", "listEntities", "getState", "callService")
        .build()

    fun chat(sessionId: String, message: String): String {
        println("SESSION [$sessionId] USER: $message")
        
        val response = chatClient.prompt()
            .user(message)
            .advisors { a -> a.param(ChatMemory.CONVERSATION_ID, sessionId) }
            .call()
            .content() ?: ""
            
        println("SESSION [$sessionId] MODEL: $response")
        return response
    }
}

