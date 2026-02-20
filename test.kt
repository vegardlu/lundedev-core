import com.google.genai.Client

fun main() {
    val client = Client.builder().build()
    println(client.models.javaClass.name)
}
