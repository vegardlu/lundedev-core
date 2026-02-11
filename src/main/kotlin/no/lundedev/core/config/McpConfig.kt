package no.lundedev.core.config

import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpConfig {
    @Bean
    fun mcpServer(): Server {
        val implementation = Implementation("lundedev-core", "1.0.0")
        val options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
                resources = ServerCapabilities.Resources(listChanged = true, subscribe = true)
            )
        )
        // Trying to pass instructions as 3rd arg assuming previous error `No value passed for parameter 'instructions'`
        // but verifying if it compiles.
        // Note: I am rewriting the file to ensure clean state from earlier edits.
        // But wait, Server constructor might take (implementation, options) and options contain instructions?
        // No, error said "No value passed for parameter 'instructions'" at the call site of Server constructor.
        return Server(implementation, options) 
    }
}
