package no.lundedev.core.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "Hello", description = "Hello World endpoints")
class HelloController {

    @GetMapping("/hello")
    @Operation(
        summary = "Say hello (requires login)",
        description = "Returns a hello message with user info. For API calls, send Bearer token in Authorization header."
    )
    fun hello(authentication: Authentication): Map<String, Any?> {
        return when (val principal = authentication.principal) {
            is Jwt -> mapOf(
                "message" to "Hello, World!",
                "user" to principal.getClaimAsString("name"),
                "email" to principal.getClaimAsString("email"),
                "subject" to principal.subject
            )
            is OAuth2User -> mapOf(
                "message" to "Hello, World!",
                "user" to principal.getAttribute<String>("name"),
                "email" to principal.getAttribute<String>("email"),
                "subject" to principal.name
            )
            else -> mapOf(
                "message" to "Hello, World!",
                "user" to authentication.name
            )
        }
    }

    @GetMapping("/public/hello")
    @Operation(
        summary = "Public hello",
        description = "Returns a hello message without authentication"
    )
    fun publicHello(): Map<String, String> {
        return mapOf("message" to "Hello, World! (public)")
    }
}
