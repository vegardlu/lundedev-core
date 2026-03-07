package no.lundedev.core

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class FindMethodsTest {
    @Test
    fun printMethods() {
        println("=== METHODS_START ===")
        val methods = ChatClient.Builder::class.java.methods
        methods.forEach {
            if (it.name.contains("tool", ignoreCase = true) || it.name.contains("function", ignoreCase = true)) {
                println(it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + ")")
            }
        }
        println("=== METHODS_END ===")
    }
}
