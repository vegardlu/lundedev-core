package no.lundedev.core

import org.springframework.ai.chat.client.ChatClient

fun main() {
    val methods = ChatClient.Builder::class.java.methods
    methods.forEach {
        if (it.name.contains("tool", ignoreCase = true) || it.name.contains("function", ignoreCase = true)) {
            println(it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + ")")
        }
    }
}
