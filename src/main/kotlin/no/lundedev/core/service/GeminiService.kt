package no.lundedev.core.service

import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.FunctionDeclaration
import com.google.cloud.vertexai.api.Schema
import com.google.cloud.vertexai.api.Tool
import com.google.cloud.vertexai.api.Type
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.cloud.vertexai.generativeai.ChatSession
import com.google.cloud.vertexai.generativeai.ContentMaker
import com.google.cloud.vertexai.generativeai.PartMaker
import com.google.cloud.vertexai.generativeai.ResponseHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.concurrent.ConcurrentHashMap
import no.lundedev.core.util.JsonUtils
import com.google.cloud.vertexai.api.GenerateContentResponse

@Service
class GeminiService(
    private val homeAssistantMcpService: HomeAssistantMcpService,
    @Value("\${google.cloud.project-id:lundedev-core}") private val projectId: String,
    @Value("\${google.cloud.location:europe-west1}") private val location: String
) {
    private lateinit var vertexAi: VertexAI
    private lateinit var model: GenerativeModel
    private val chatSessions = ConcurrentHashMap<String, ChatSession>()

    @PostConstruct
    fun init() {
        vertexAi = VertexAI(projectId, location)
        
        val systemInstruction = ContentMaker.fromString("""
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
        """.trimIndent())

        model = GenerativeModel.Builder()
            .setModelName("gemini-2.5-flash")
            .setVertexAi(vertexAi)
            .setTools(listOf(getTools()))
            .setSystemInstruction(systemInstruction)
            .build()
    }
    
    private fun getTools(): Tool {
        val listAreasFunc = FunctionDeclaration.newBuilder()
            .setName("list_areas")
            .setDescription("List all configured areas (rooms) in the home.")
            .build()

        val listEntitiesFunc = FunctionDeclaration.newBuilder()
            .setName("list_entities")
            .setDescription("List entities. If you filter by area/domain and nothing is found, I will return ALL entities so you can find it yourself.")
            .setParameters(
                Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("domain", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Optional domain filter.")
                        .build())
                    .putProperties("area", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Optional area filter.")
                        .build())
                    .build()
            )
            .build()
            
        val getStateFunc = FunctionDeclaration.newBuilder()
            .setName("get_state")
            .setDescription("Get the current state and attributes of a specific entity.")
            .setParameters(
                Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("entity_id", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("The entity ID (e.g., 'light.kitchen_lights').")
                        .build())
                    .addRequired("entity_id")
                    .build()
            )
            .build()
            
        val callServiceFunc = FunctionDeclaration.newBuilder()
            .setName("call_service")
            .setDescription("Call a service on a home assistant domain to control devices (e.g., turn light on/off).")
            .setParameters(
                Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("domain", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("The domain (e.g., 'light').")
                        .build())
                    .putProperties("service", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("The service to call (e.g., 'turn_on', 'turn_off').")
                        .build())
                    .putProperties("entity_id", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("The entity ID to target.")
                        .build())
                    .putProperties("payload_json", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Optional JSON string for additional parameters like brightness, color, etc.")
                        .build())    
                    .addRequired("domain")
                    .addRequired("service")
                    .addRequired("entity_id")
                    .build()
            )
            .build()
            
        return Tool.newBuilder()
            .addFunctionDeclarations(listAreasFunc)
            .addFunctionDeclarations(listEntitiesFunc)
            .addFunctionDeclarations(getStateFunc)
            .addFunctionDeclarations(callServiceFunc)
            .build()
    }

    @PreDestroy
    fun close() {
        if (::vertexAi.isInitialized) {
            vertexAi.close()
        }
    }

    fun chat(sessionId: String, message: String): String {
        val chat = chatSessions.computeIfAbsent(sessionId) { 
            model.startChat()
        }
        
        var response = chat.sendMessage(message)
        
        // Loop to handle potential multiple function calls
        while (isFunctionCall(response)) {
             val content = response.candidatesList[0].content
             val functionCalls = content.partsList.filter { it.hasFunctionCall() }

             val functionResponses = functionCalls.map { part ->
                 val functionCall = part.functionCall
                 val functionName = functionCall.name
                 val args = functionCall.args.fieldsMap
                 
                 val toolResult = try {
                     when (functionName) {
                         "list_areas" -> {
                             homeAssistantMcpService.listAreas().joinToString("\n")
                         }
                         "list_entities" -> {
                             val domain = args["domain"]?.stringValue?.takeIf { it.isNotEmpty() }
                             val area = args["area"]?.stringValue?.takeIf { it.isNotEmpty() }
                             
                             var result = homeAssistantMcpService.listEntities(domain, area)
                             
                             // FALLBACK: If specific search yielded nothing, give the AI everything
                             if (result.isEmpty() && (domain != null || area != null)) {
                                 val allEntities = homeAssistantMcpService.listEntities(null, null)
                                 "No entities found with filter [domain=$domain, area=$area]. Here is the COMPLETE list of entities. Please check this list to find what the user meant:\n" + 
                                 allEntities.joinToString("\n")
                             } else {
                                 result.joinToString("\n")
                             }
                         }
                         "get_state" -> {
                             val entityId = args["entity_id"]?.stringValue ?: ""
                             homeAssistantMcpService.getState(entityId)?.toString() ?: "Entity not found"
                         }
                         "call_service" -> {
                             val domain = args["domain"]?.stringValue ?: ""
                             val service = args["service"]?.stringValue ?: ""
                             val entityId = args["entity_id"]?.stringValue ?: ""
                             val payloadJson = args["payload_json"]?.stringValue ?: "{}"
                             
                             val payload = JsonUtils.parseMap(payloadJson)
                             
                             homeAssistantMcpService.callService(domain, service, entityId, payload)
                             "Service $domain.$service called for $entityId"
                         }
                         else -> "Unknown function $functionName"
                     }
                 } catch (e: Exception) {
                     "Error executing tool: ${e.message}"
                 }
                 
                 PartMaker.fromFunctionResponse(functionName, mapOf("result" to toolResult))
             }
             
             // Send the tool results back to the model
             response = chat.sendMessage(
                 ContentMaker.fromMultiModalData(*functionResponses.toTypedArray())
             )
        }

        return ResponseHandler.getText(response)
    }
    
    private fun isFunctionCall(response: GenerateContentResponse): Boolean {
        if (response.candidatesList.isEmpty()) return false
        val content = response.candidatesList[0].content
        if (content.partsList.isEmpty()) return false
        return content.partsList.any { it.hasFunctionCall() }
    }
}
