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
        
        val listEntitiesFunc = FunctionDeclaration.newBuilder()
            .setName("list_entities")
            .setDescription("List all available entities (devices) in the home, optionally filtered by domain.")
            .setParameters(
                Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("domain", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("The domain to filter by (e.g., 'light', 'switch').")
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

        val tool = Tool.newBuilder()
            .addFunctionDeclarations(listEntitiesFunc)
            .addFunctionDeclarations(getStateFunc)
            .addFunctionDeclarations(callServiceFunc)
            .build()

        model = GenerativeModel.Builder()
            .setModelName("gemini-1.5-flash-001")
            .setVertexAi(vertexAi)
            .setTools(listOf(tool))
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
             // We only handle the first function call in the candidate for simplicity, 
             // but candidates could theoretically have multiple parts (?) - usually one function call per turn.
             val functionCall = response.candidatesList[0].content.partsList[0].functionCall
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
             
             // Send the tool result back to the model
             response = chat.sendMessage(
                 ContentMaker.fromMultiModalData(
                    PartMaker.fromFunctionResponse(functionName, mapOf("result" to toolResult))
                 )
             )
        }

        return ResponseHandler.getText(response)
    }
    
    private fun isFunctionCall(response: GenerateContentResponse): Boolean {
        if (response.candidatesList.isEmpty()) return false
        val content = response.candidatesList[0].content
        if (content.partsList.isEmpty()) return false
        return content.partsList[0].hasFunctionCall()
    }
}
