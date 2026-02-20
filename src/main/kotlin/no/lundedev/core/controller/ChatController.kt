package no.lundedev.core.controller

import no.lundedev.core.service.GeminiService
import org.springframework.security.core.Authentication
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
    private val logger = org.slf4j.LoggerFactory.getLogger(ChatController::class.java)

    @PostMapping
    fun chat(
        @RequestBody request: ChatRequest,
        authentication: Authentication
    ): ChatResponse {
        try {
            // Authenticated username is used as session ID to maintain per-user history
            val response = geminiService.chat(authentication.name, request.message)
            return ChatResponse(response)
        } catch (e: Exception) {
            logger.error("Error processing chat request.", e)
            throw e
        }
    }
}
