package no.lundedev.core.service

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class McpRegistry(
    private val server: Server,
    private val haService: HomeAssistantMcpService
) {
    @PostConstruct
    fun register() {
        registerListEntities()
        registerGetState()
        registerCallService()
    }

    private fun registerListEntities() {
        server.addTool(
            Tool(
                name = "list_entities",
                description = "List all entities, optionally filtered by domain. Returns pipe-separated values: id|name|area|floor|state",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        put("domain", buildJsonObject {
                            put("type", "string")
                            put("description", "Domain to filter by (e.g. 'light', 'switch')")
                        })
                    }
                )
            ),
            { request ->
                val args = request.arguments ?: buildJsonObject {}
                val domain = (args["domain"] as? JsonPrimitive)?.content
                val entities = haService.listEntities(domain)
                CallToolResult(content = listOf(TextContent(entities.joinToString("\n"))))
            }
        )
    }

    private fun registerGetState() {
        server.addTool(
            Tool(
                name = "get_state",
                description = "Get the state and attributes of a specific entity",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        put("entity_id", buildJsonObject {
                            put("type", "string")
                            put("description", "The entity ID (e.g. 'light.kitchen')")
                        })
                    },
                    required = listOf("entity_id")
                )
            ),
            { request ->
                val args = request.arguments ?: buildJsonObject {}
                val entityId = (args["entity_id"] as? JsonPrimitive)?.content
                    ?: throw IllegalArgumentException("entity_id is required")
                val state = haService.getState(entityId)
                if (state != null) {
                    CallToolResult(content = listOf(TextContent(state.toString()))) // Simplistic serialization
                } else {
                    CallToolResult(content = listOf(TextContent("Entity not found")), isError = true)
                }
            }
        )
    }

    private fun registerCallService() {
        server.addTool(
            Tool(
                name = "call_service",
                description = "Call a service on a domain",
                inputSchema = Tool.Input(
                    properties = buildJsonObject {
                        put("domain", buildJsonObject {
                            put("type", "string")
                            put("description", "The domain (e.g. 'light')")
                        })
                        put("service", buildJsonObject {
                            put("type", "string")
                            put("description", "The service (e.g. 'turn_on')")
                        })
                        put("entity_id", buildJsonObject {
                            put("type", "string")
                            put("description", "The entity ID to target")
                        })
                        put("payload", buildJsonObject {
                            put("type", "object")
                            put("description", "Optional payload parameters")
                        })
                    },
                    required = listOf("domain", "service", "entity_id")
                )
            ),
            { request ->
                val args = request.arguments ?: buildJsonObject {}
                val domain = (args["domain"] as? JsonPrimitive)?.content
                    ?: throw IllegalArgumentException("domain is required")
                val service = (args["service"] as? JsonPrimitive)?.content
                    ?: throw IllegalArgumentException("service is required")
                val entityId = (args["entity_id"] as? JsonPrimitive)?.content
                    ?: throw IllegalArgumentException("entity_id is required")
                
                val payloadJson = args["payload"] as? JsonObject
                val payload = payloadJson?.mapValues { entry -> 
                    (entry.value as? JsonPrimitive)?.content ?: entry.value.toString()
                } ?: emptyMap()

                haService.callService(domain, service, entityId, payload)
                CallToolResult(content = listOf(TextContent("Service called successfully")))
            }
        )
    }
}
