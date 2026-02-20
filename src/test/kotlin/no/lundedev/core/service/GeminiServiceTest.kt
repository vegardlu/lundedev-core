package no.lundedev.core.service

import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class GeminiServiceTest {

    private val toolConfig = mockk<HomeAssistantToolConfig>()
    private val client = mockk<Client>()
    private val models = mockk<Models>()
    private lateinit var geminiService: GeminiService

    @BeforeEach
    fun setUp() {
        // Construct service with mocked dependencies
        geminiService = GeminiService(toolConfig, "test-project", "test-location")

        // We use reflection or inject the mock client since it is initialized in @PostConstruct
        val field = GeminiService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(geminiService, client)
        
        every { client.models } returns models
    }

    @Test
    fun `chat handles simple text response without tools`() {
        val expectedText = "Hello from Google GenAI!"
        val response = mockk<GenerateContentResponse>()
        every { response.functionCalls() } returns null
        every { response.text() } returns expectedText
        
        val candidate = mockk<Candidate>()
        every { candidate.content() } returns Optional.of(Content.builder().role("model").build())
        every { response.candidates() } returns Optional.of(listOf(candidate))

        val anyList = mutableListOf<Content>()
        every { models.generateContent(any(), anyList, any<GenerateContentConfig>()) } returns response

        val result = geminiService.chat("session-1", "Hi there")

        assertEquals(expectedText, result)
        verify(exactly = 1) { models.generateContent(any(), anyList, any<GenerateContentConfig>()) }
    }

}
