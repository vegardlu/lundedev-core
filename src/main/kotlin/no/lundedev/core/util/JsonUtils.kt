package no.lundedev.core.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object JsonUtils {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun parseMap(json: String): Map<String, Any> =
        try {
            if (json.isBlank()) return emptyMap()
            mapper.readValue(json)
        } catch (e: Exception) {
            emptyMap()
        }
}
