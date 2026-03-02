package no.lundedev.core.service

import no.lundedev.core.util.JsonUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Description
import java.util.function.Function

@Configuration
class HomeAssistantToolConfig {

    class EmptyRequest

    data class ListEntitiesRequest(
        val domain: String? = null,
        val area: String? = null
    )

    data class GetStateRequest(
        val entity_id: String
    )

    data class CallServiceRequest(
        val domain: String,
        val service: String,
        val entity_id: String,
        val payload_json: String? = null
    )

    @Bean
    @Description("List all configured areas (rooms) in the home.")
    fun listAreas(homeAssistantService: HomeAssistantService): Function<EmptyRequest, String> = Function {
        homeAssistantService.listAreas().joinToString("\n")
    }

    @Bean
    @Description("List entities. If you filter by area/domain and nothing is found, I will return ALL entities so you can find it yourself.")
    fun listEntities(homeAssistantService: HomeAssistantService): Function<ListEntitiesRequest, String> = Function { request ->
        var result = homeAssistantService.listEntities(request.domain?.takeIf { it.isNotEmpty() }, request.area?.takeIf { it.isNotEmpty() })
        
        if (result.isEmpty() && (request.domain != null || request.area != null)) {
            val allEntities = homeAssistantService.listEntities(null, null)
            "No entities found with filter [domain=${request.domain}, area=${request.area}]. Here is the COMPLETE list of entities. Please check this list to find what the user meant:\n" + 
            allEntities.joinToString("\n")
        } else if (result.isEmpty()) {
            "No entities found. The system might be disconnected or there are no devices."
        } else {
            result.joinToString("\n")
        }
    }

    @Bean
    @Description("Get the current state and attributes of a specific entity.")
    fun getState(homeAssistantService: HomeAssistantService): Function<GetStateRequest, String> = Function { request ->
        if (request.entity_id.isBlank()) "Error: entity_id is required" 
        else homeAssistantService.getState(request.entity_id)?.toString() ?: "Entity not found"
    }

    @Bean
    @Description("Call a service on a home assistant domain to control devices (e.g., turn light on/off).")
    fun callService(homeAssistantService: HomeAssistantService): Function<CallServiceRequest, String> = Function { request ->
        val payload = request.payload_json?.takeIf { it.isNotBlank() }?.let { JsonUtils.parseMap(it) } ?: emptyMap()
        homeAssistantService.callService(request.domain, request.service, request.entity_id, payload)
        "Service ${request.domain}.${request.service} called for ${request.entity_id}"
    }
}
