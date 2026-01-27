package no.lundedev.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/actuator/health/**").permitAll()
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2.defaultSuccessUrl("/swagger-ui.html", true)
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
            .exceptionHandling { exceptions ->
                val bearerEntryPoint = BearerTokenAuthenticationEntryPoint()
                val loginEntryPoint = LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")
                exceptions.authenticationEntryPoint { request, response, authException ->
                    if (request.getHeader("Authorization")?.startsWith("Bearer ") == true) {
                        bearerEntryPoint.commence(request, response, authException)
                    } else {
                        loginEntryPoint.commence(request, response, authException)
                    }
                }
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
        val issuer = CommonOAuth2Provider.GOOGLE
            .getBuilder("google")
            .clientId("dummy")
            .build()
            .providerDetails
            .issuerUri
        return JwtDecoders.fromIssuerLocation(issuer)
    }
}
