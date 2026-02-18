package no.lundedev.core.service

import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.Tool
import com.google.genai.types.Type
import no.lundedev.core.util.JsonUtils
import org.springframework.stereotype.Component

@Component
class HomeAssistantToolConfig(
    private val homeAssistantService: HomeAssistantService
) {
    fun getTool(): Tool {
        return Tool.builder()
            .functionDeclarations(listAreasFunc, listEntitiesFunc, getStateFunc, callServiceFunc)
            .build()
    }

    fun execute(functionName: String, args: Map<String, Any?>): String {
        return try {
            when (functionName) {
                "list_areas" -> {
                    homeAssistantService.listAreas().joinToString("\n")
                }
                "list_entities" -> {
                    val domain = args["domain"] as? String
                    val area = args["area"] as? String
                    
                    var result = homeAssistantService.listEntities(domain?.takeIf { it.isNotEmpty() }, area?.takeIf { it.isNotEmpty() })
                    
                    if (result.isEmpty() && (domain != null || area != null)) {
                        val allEntities = homeAssistantService.listEntities(null, null)
                        "No entities found with filter [domain=$domain, area=$area]. Here is the COMPLETE list of entities. Please check this list to find what the user meant:\n" + 
                        allEntities.joinToString("\n")
                    } else if (result.isEmpty()) {
                        "No entities found. The system might be disconnected or there are no devices."
                    } else {
                        result.joinToString("\n")
                    }
                }
                "get_state" -> {
                    val entityId = args["entity_id"] as? String ?: ""
                    if (entityId.isBlank()) "Error: entity_id is required" 
                    else homeAssistantService.getState(entityId)?.toString() ?: "Entity not found"
                }
                "call_service" -> {
                    val domain = args["domain"] as? String ?: ""
                    val service = args["service"] as? String ?: ""
                    val entityId = args["entity_id"] as? String ?: ""
                    val payloadJson = args["payload_json"] as? String ?: "{}"
                    
                    val payload = JsonUtils.parseMap(payloadJson)
                    
                    homeAssistantService.callService(domain, service, entityId, payload)
                    "Service $domain.$service called for $entityId"
                }
                else -> "Unknown function $functionName"
            }
        } catch (e: Exception) {
            "Error executing tool $functionName: ${e.message}"
        }
    }

    private val listAreasFunc = FunctionDeclaration.builder()
        .name("list_areas")
        .description("List all configured areas (rooms) in the home.")
        .build()

    private val listEntitiesFunc = FunctionDeclaration.builder()
        .name("list_entities")
        .description("List entities. If you filter by area/domain and nothing is found, I will return ALL entities so you can find it yourself.")
        .parameters(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(mapOf(
                    "domain" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("Optional domain filter.")
                        .build(),
                    "area" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("Optional area filter.")
                        .build()
                ))
                .build()
        )
        .build()
        
    private val getStateFunc = FunctionDeclaration.builder()
        .name("get_state")
        .description("Get the current state and attributes of a specific entity.")
        .parameters(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(mapOf(
                    "entity_id" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("The entity ID (e.g., 'light.kitchen_lights').")
                        .build()
                ))
                .required(listOf("entity_id"))
                .build()
        )
        .build()
        
    private val callServiceFunc = FunctionDeclaration.builder()
        .name("call_service")
        .description("Call a service on a home assistant domain to control devices (e.g., turn light on/off).")
        .parameters(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(mapOf(
                    "domain" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("The domain (e.g., 'light').")
                        .build(),
                    "service" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("The service to call (e.g., 'turn_on', 'turn_off').")
                        .build(),
                    "entity_id" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("The entity ID to target.")
                        .build(),
                    "payload_json" to Schema.builder()
                        .type(Type.Known.STRING)
                        .description("Optional JSON string for additional parameters like brightness, color, etc.")
                        .build()
                ))
                .required(listOf("domain", "service", "entity_id"))
                .build()
        )
        .build()
}
