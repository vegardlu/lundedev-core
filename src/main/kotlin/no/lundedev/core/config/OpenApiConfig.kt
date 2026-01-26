package no.lundedev.core.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Lundedev Core API")
                    .version("0.0.1")
                    .description(
                        """
                        API documentation for Lundedev Core.
                        
                        **Authentication:** [Click here to login with Google](/oauth2/authorization/google) | [Logout](/logout)
                        """.trimIndent()
                    )
            )
    }
}
