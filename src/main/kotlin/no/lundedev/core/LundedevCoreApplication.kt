package no.lundedev.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LundedevCoreApplication

fun main(args: Array<String>) {
    runApplication<LundedevCoreApplication>(*args)
}
