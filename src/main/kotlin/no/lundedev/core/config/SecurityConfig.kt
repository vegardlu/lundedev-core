package no.lundedev.core.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "app.security")
@Component
data class SecurityProperties(
    val allowedClientIds: List<String> = emptyList()
)

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val securityProperties: SecurityProperties
) {

    companion object {
        private const val GOOGLE_ISSUER_URI = "https://accounts.google.com"

        private val PUBLIC_ENDPOINTS = arrayOf(
            "/api/public/**",
            "/actuator/health/**"
        )

        private val SWAGGER_ENDPOINTS = arrayOf(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml"
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(*PUBLIC_ENDPOINTS).permitAll()
                    .requestMatchers(*SWAGGER_ENDPOINTS).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2.defaultSuccessUrl("/swagger-ui.html", true)
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(DelegatingAuthenticationEntryPoint())
            }
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/swagger-ui.html")
                    .permitAll()
            }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .build()

        jwtDecoder.setJwtValidator(jwtValidator())
        return jwtDecoder
    }

    private fun jwtValidator(): OAuth2TokenValidator<Jwt> {
        val defaultValidators = JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER_URI)
        val audienceValidator = audienceValidator()
        return DelegatingOAuth2TokenValidator(defaultValidators, audienceValidator)
    }

    private fun audienceValidator(): OAuth2TokenValidator<Jwt> {
        val allowedAudiences = securityProperties.allowedClientIds.toSet()
        return JwtClaimValidator<List<String>>("aud") { audiences ->
            audiences != null && audiences.any { it in allowedAudiences }
        }
    }

    /**
     * Entry point that delegates to Bearer token handling for API requests
     * or redirects to OAuth login for browser requests.
     */
    private class DelegatingAuthenticationEntryPoint : org.springframework.security.web.AuthenticationEntryPoint {
        private val bearerEntryPoint = BearerTokenAuthenticationEntryPoint()
        private val loginEntryPoint = LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")

        override fun commence(
            request: jakarta.servlet.http.HttpServletRequest,
            response: jakarta.servlet.http.HttpServletResponse,
            authException: org.springframework.security.core.AuthenticationException
        ) {
            if (request.getHeader("Authorization")?.startsWith("Bearer ") == true) {
                bearerEntryPoint.commence(request, response, authException)
            } else {
                loginEntryPoint.commence(request, response, authException)
            }
        }
    }
}
