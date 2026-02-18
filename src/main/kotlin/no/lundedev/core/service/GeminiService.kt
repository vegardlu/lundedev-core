package no.lundedev.core.service

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import no.lundedev.core.util.JsonUtils

@Service
class GeminiService(
    private val toolConfig: HomeAssistantToolConfig,
    @Value("\${google.cloud.project-id:lundedev-core}") private val projectId: String,
    @Value("\${google.cloud.location:europe-west1}") private val location: String
) {
    private lateinit var client: Client
    // Map sessionId to a list of Content (chat history)
    private val chatHistories = ConcurrentHashMap<String, MutableList<Content>>()
    private val systemInstructionText = """
            You are a smart home assistant for the 'Lundedev' home.
            Your goal is to help the user control their home and get information about it.
            
            ### TOOLS & DATA FORMAT
            You have access to tools to interact with Home Assistant.
            
            1. `list_areas()`:
               - Returns a list of all configured rooms/areas (e.g. "Living Room", "Kitchen").
               - USE THIS to discover valid room names.
               
            2. `list_entities(domain: String?, area: String?)`: 
               - Returns a list of entities.
               - text format: `entity_id|friendly_name|area|floor|state|device_class|unit`.
               - `area`: Filter by area name (e.g. "Living Room"). Case-insensitive.
               - `domain`: Optional filter. AVOID using this if you are looking for "lights" in a room, because some lights are `switch` entities. Just perform `list_entities(area='...')` and check the names/types yourself.
               
            3. `get_state(entity_id: String)`:
               - Returns detailed state and attributes.
               
            4. `call_service(...)`:
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
              1. Call `list_entities(area='Living Room')`.
              2. **CRITICAL**: If that returns nothing or the wrong thing, look at the **FULL LIST** (if provided).
              3. Search the full list for any entity that contains `living` or `stua` in its ID or Name.
              4. **Lights are not just `light.*` domain**. They can be `switch.*` (e.g. `switch.living_room_lights`).
            
            - **"All Rooms"**: If the user asks "temperature in all rooms":
              1. Call `list_areas()` then `list_entities` for sensors.
              
            - **Fuzzy Matching & Deduction**:
              - **ALWAYS** try to find a match. Do not say "I can't find it" unless you have searched the FULL entity list.
              - If `list_entities` gives you a huge list, **READ IT**.
              - Example: User says "stua". You see `light.living_room_ceiling`. -> MATCH. Control it.
            
            Always be helpful, concise, and natural.
        """.trimIndent()

    @PostConstruct
    fun init() {
        client = Client.builder()
            .vertexAI(true)
            .project(projectId)
            .location(location)
            .build()
    }

    fun chat(sessionId: String, message: String): String {
        val history = chatHistories.computeIfAbsent(sessionId) { mutableListOf() }
        
        // Add user message to history
        val userContent = Content.builder()
            .role("user")
            .parts(listOf(Part.builder().text(message).build()))
            .build()
        history.add(userContent)

        var currentResponse = generateResponse(history)
        
        // Loop to handle potential multiple function calls
        while (isFunctionCall(currentResponse)) {
            // Add model's response (with function call) to history
            // We need to reconstruct the content from the response candidates to add to history
            val candidates = currentResponse.candidates().orElse(emptyList())
            val modelContent = if (candidates.isNotEmpty()) {
                candidates[0].content().orElse(
                    Content.builder().role("model").build()
                )
            } else {
                 Content.builder().role("model").build()
            }
            history.add(modelContent)
            
            val functionCalls = currentResponse.functionCalls() ?: emptyList()

            val functionResponses = functionCalls.map { functionCall ->
                val functionName = functionCall.name().get()
                val args = functionCall.args().orElse(emptyMap())
                
                val toolResult = toolConfig.execute(functionName, args)
                
                Part.fromFunctionResponse(functionName, mapOf("result" to toolResult))
            }
            
            // Add tool response to history
            val toolContent = Content.builder()
                .role("function") 
                .parts(functionResponses)
                .build()
                
            history.add(toolContent)
            
            // Generate next response
            currentResponse = generateResponse(history)
        }
        
        // Add final model response to history
        val candidates = currentResponse.candidates().orElse(emptyList())
        val finalModelContent = if (candidates.isNotEmpty()) {
            candidates[0].content().orElse(
                Content.builder().role("model").build()
            )
        } else {
             Content.builder().role("model").build()
        }
        history.add(finalModelContent)

        return currentResponse.text() ?: ""
    }

    private fun generateResponse(history: List<Content>): GenerateContentResponse {
        val config = GenerateContentConfig.builder()
            .systemInstruction(
                Content.builder().parts(listOf(Part.builder().text(systemInstructionText).build())).build()
            )
            .tools(listOf(toolConfig.getTool()))
            .build()

        return client.models.generateContent(
            "gemini-2.5-flash",
            history,
            config
        )
    }

    private fun isFunctionCall(response: GenerateContentResponse): Boolean {
        return try {
            response.functionCalls()?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
    }
}

