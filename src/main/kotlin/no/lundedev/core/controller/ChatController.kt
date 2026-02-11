package no.lundedev.core.controller

import no.lundedev.core.service.GeminiService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestHeader

data class ChatRequest(val message: String)
data class ChatResponse(val response: String)

@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val geminiService: GeminiService
) {

    @PostMapping
    fun chat(
        @RequestBody request: ChatRequest,
        @RequestHeader("X-Session-Id", required = false) sessionId: String?
    ): ChatResponse {
        // Use provided session ID or default to a generic one per user if we had user context here.
        // For now, let's use a default session or generate one. 
        // In a real app, the session ID should probably come from the client to maintain history.
        val activeSessionId = sessionId ?: "default-session"
        
        val responseText = geminiService.chat(activeSessionId, request.message)
        return ChatResponse(responseText)
    }
}
