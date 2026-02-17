package no.lundedev.core.service

import com.google.cloud.vertexai.api.FunctionDeclaration
import com.google.cloud.vertexai.api.Schema
import com.google.cloud.vertexai.api.Tool
import com.google.cloud.vertexai.api.Type
import no.lundedev.core.util.JsonUtils
import org.springframework.stereotype.Component

@Component
class HomeAssistantToolConfig(
    private val homeAssistantService: HomeAssistantService
) {
    fun getTool(): Tool {
        return Tool.newBuilder()
            .addFunctionDeclarations(listAreasFunc)
            .addFunctionDeclarations(listEntitiesFunc)
            .addFunctionDeclarations(getStateFunc)
            .addFunctionDeclarations(callServiceFunc)
            .build()
    }

    fun execute(functionName: String, args: Map<String, com.google.protobuf.Value>): String {
        return try {
            when (functionName) {
                "list_areas" -> {
                    homeAssistantService.listAreas().joinToString("\n")
                }
                "list_entities" -> {
                    val domain = args["domain"]?.stringValue?.takeIf { it.isNotEmpty() }
                    val area = args["area"]?.stringValue?.takeIf { it.isNotEmpty() }
                    
                    var result = homeAssistantService.listEntities(domain, area)
                    
                    if (result.isEmpty() && (domain != null || area != null)) {
                        val allEntities = homeAssistantService.listEntities(null, null)
                        "No entities found with filter [domain=$domain, area=$area]. Here is the COMPLETE list of entities. Please check this list to find what the user meant:\n" + 
                        allEntities.joinToString("\n")
                    } else {
                        result.joinToString("\n")
                    }
                }
                "get_state" -> {
                    val entityId = args["entity_id"]?.stringValue ?: ""
                    if (entityId.isBlank()) "Error: entity_id is required" 
                    else homeAssistantService.getState(entityId)?.toString() ?: "Entity not found"
                }
                "call_service" -> {
                    val domain = args["domain"]?.stringValue ?: ""
                    val service = args["service"]?.stringValue ?: ""
                    val entityId = args["entity_id"]?.stringValue ?: ""
                    val payloadJson = args["payload_json"]?.stringValue ?: "{}"
                    
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

    private val listAreasFunc = FunctionDeclaration.newBuilder()
        .setName("list_areas")
        .setDescription("List all configured areas (rooms) in the home.")
        .build()

    private val listEntitiesFunc = FunctionDeclaration.newBuilder()
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
        
    private val getStateFunc = FunctionDeclaration.newBuilder()
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
        
    private val callServiceFunc = FunctionDeclaration.newBuilder()
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
}
