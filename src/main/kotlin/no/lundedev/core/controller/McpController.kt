package no.lundedev.core.controller

import io.modelcontextprotocol.kotlin.sdk.server.Server
import no.lundedev.core.mcp.SpringSseTransport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import jakarta.annotation.PreDestroy

@RestController
@RequestMapping("/mcp")
class McpController(
    private val server: Server
) {
    private val transport = SpringSseTransport("/mcp/message")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            server.connect(transport)
        }
    }

    @GetMapping("/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun handleSse(): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        transport.addEmitter(emitter)
        return emitter
    }

    @PostMapping("/message")
    suspend fun handleMessage(@RequestBody message: String) {
        transport.handleMessage(message)
    }
    
    @PreDestroy
    fun destroy() {
        scope.cancel()
    }
}
