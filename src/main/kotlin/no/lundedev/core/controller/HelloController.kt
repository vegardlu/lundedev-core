package no.lundedev.core.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
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
        description = "Returns a hello message with user info. Login via the link in the API description above."
    )
    fun hello(@AuthenticationPrincipal user: OAuth2User): Map<String, Any?> {
        return mapOf(
            "message" to "Hello, World!",
            "user" to user.getAttribute<String>("name"),
            "email" to user.getAttribute<String>("email")
        )
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
