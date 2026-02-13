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
            
            1. `list_entities(domain: String?)`: 
               - Returns a list of entities in the format: `entity_id|friendly_name|area|floor|state|device_class|unit`.
               - Example output: `light.kitchen_ceiling|Kitchen Ceiling Light|Kitchen|1st Floor|on|light|`
               - USE THIS to find entity IDs when the user asks about a room or a device name.
               
            2. `get_state(entity_id: String)`:
               - Returns detailed state and attributes for a specific entity.
               
            3. `call_service(domain, service, entity_id, payload_json)`:
               - Controls devices. Common services: `turn_on`, `turn_off`, `toggle`.
            
            ### REASONING & DEDUCTION
            - **Room Context**: If the user asks "turn on lights in the living room", you must:
              1. Call `list_entities(domain='light')`.
              2. Filter the results where `area` is 'Living Room' (or similar).
              3. Call `call_service` for EACH matching entity.
            - **"All Rooms"**: If the user asks "temperature in all rooms":
              1. Call `list_entities(domain='sensor')`.
              2. Filter for temperature sensors (look at device_class or unit).
              3. Group the results by `area` and report the temperature for each room.
            - **Vague Requests**: If the user says "turn off the light" without specifying which one, look for lights that are currently 'on' in the most likely area or ask for clarification listing the active lights.
            - **Entity IDs**: NEVER ask the user for an `entity_id`. It is your job to find it using `list_entities`.
            
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
        val listEntitiesFunc = FunctionDeclaration.newBuilder()
            .setName("list_entities")
            .setDescription("List all available entities in the home with details (id|name|area|floor|state|device_class|unit).")
            .setParameters(
                Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("domain", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("The domain to filter by (e.g., 'light', 'switch', 'sensor').")
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
                         "list_entities" -> {
                             val domain = args["domain"]?.stringValue?.takeIf { it.isNotEmpty() }
                             // Just joining with newline for cleaner prompt context
                             homeAssistantMcpService.listEntities(domain).joinToString("\n")
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
