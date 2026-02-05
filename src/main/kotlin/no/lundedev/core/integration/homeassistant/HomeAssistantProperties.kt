package no.lundedev.core.integration.homeassistant

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "homeassistant")
data class HomeAssistantProperties(
    val url: String,
    val token: String
)
