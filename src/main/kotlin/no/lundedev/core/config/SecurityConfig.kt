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
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

import org.springframework.boot.context.properties.EnableConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val allowedClientIds: List<String> = emptyList()
)

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties::class)
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
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/dashboard/**").authenticated()
                    .requestMatchers("/api/chat/**").authenticated() // Allow chat
                    .anyRequest().authenticated()
            }
            .csrf { it.disable() } // Disable CSRF for API consistency
            .cors { } // Enable CORS
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
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf("https://hjem.lundeberg.cc", "http://localhost:3000", "http://localhost:5173") // Allow frontend & inspector
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
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
        val logger = org.slf4j.LoggerFactory.getLogger(SecurityConfig::class.java)
        
        println("DEBUG: SecurityConfig initialized with allowedAudiences: $allowedAudiences")

        return JwtClaimValidator<List<String>>("aud") { audiences ->
            println("DEBUG: Validating JWT audiences: $audiences against allowed: $allowedAudiences")
            
            val match = audiences != null && audiences.any { it in allowedAudiences }
            if (!match) {
                logger.warn("JWT Audience mismatch! Expected one of: $allowedAudiences, but Token had: $audiences")
                println("DEBUG: JWT Audience mismatch! Expected one of: $allowedAudiences, but Token had: $audiences")
            }
            match
        }
    }

    /**
     * Entry point that delegates to Bearer token handling for API requests
     * or redirects to OAuth login for browser requests.
     */
    private class DelegatingAuthenticationEntryPoint : org.springframework.security.web.AuthenticationEntryPoint {
        private val bearerEntryPoint = BearerTokenAuthenticationEntryPoint()
        private val loginEntryPoint = LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")
        private val logger = org.slf4j.LoggerFactory.getLogger(DelegatingAuthenticationEntryPoint::class.java)

        override fun commence(
            request: jakarta.servlet.http.HttpServletRequest,
            response: jakarta.servlet.http.HttpServletResponse,
            authException: org.springframework.security.core.AuthenticationException
        ) {
            logger.error("Authentication failed for path ${request.requestURI}: ${authException.message}", authException)
            
            if (request.getHeader("Authorization")?.startsWith("Bearer ") == true) {
                bearerEntryPoint.commence(request, response, authException)
            } else {
                loginEntryPoint.commence(request, response, authException)
            }
        }
    }
}
