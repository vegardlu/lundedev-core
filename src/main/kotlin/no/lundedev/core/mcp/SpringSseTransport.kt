package no.lundedev.core.mcp

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class SpringSseTransport(
    private val endpoint: String
) : Transport {
    private var messageHandler: (suspend (JSONRPCMessage) -> Unit)? = null
    private val emitters = CopyOnWriteArrayList<SseEmitter>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun start() {
        // Ready to receive
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        val data = json.encodeToString(message)
        emitters.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().data(data))
            } catch (e: Exception) {
                emitters.remove(emitter)
            }
        }
    }

    override fun onClose(block: () -> Unit) {
        // No op for now as we have multiple emitters
        // Or register a global close handler?
    }

    override fun onError(block: (Throwable) -> Unit) {
        // No op
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        messageHandler = block
    }

    override suspend fun close() {
        emitters.forEach { it.complete() }
        emitters.clear()
    }

    fun addEmitter(emitter: SseEmitter) {
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        
        try {
            emitter.send(SseEmitter.event().name("endpoint").data(endpoint))
        } catch (e: Exception) {
            emitters.remove(emitter)
        }
    }

    suspend fun handleMessage(message: String) {
        val msg = json.decodeFromString<JSONRPCMessage>(message)
        messageHandler?.invoke(msg)
    }
}
