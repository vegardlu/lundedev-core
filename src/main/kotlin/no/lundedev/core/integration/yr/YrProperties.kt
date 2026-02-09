package no.lundedev.core.integration.yr

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "integration.yr")
data class YrProperties(
    val url: String = "https://api.met.no/weatherapi/locationforecast/2.0",
    val userAgent: String = "lundedev-core/0.0.1 github.com/vegardlu/homelab"
)
