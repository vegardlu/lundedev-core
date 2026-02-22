package no.lundedev.core.controller

import no.lundedev.core.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/auth")
class PublicAuthController(
    private val userRepository: UserRepository
) {

    @GetMapping("/verify")
    fun verifyEmail(@RequestParam email: String): ResponseEntity<Void> {
        val user = userRepository.findByEmail(email)
        return if (user != null) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.status(403).build()
        }
    }
}
