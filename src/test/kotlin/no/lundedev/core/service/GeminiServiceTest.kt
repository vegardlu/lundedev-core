package no.lundedev.core.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor

class GeminiServiceTest {

    private val chatClient = mockk<ChatClient>()
    private val requestSpec = mockk<ChatClient.PromptUserSpec>()
    private val advisorSpec = mockk<ChatClient.AdvisorSpec>()
    private val callResponseSpec = mockk<ChatClient.CallResponseSpec>()
    
    private lateinit var geminiService: GeminiService

    @BeforeEach
    fun setUp() {
        val builder = mockk<ChatClient.Builder>()
        
        every { builder.defaultSystem(any<String>()) } returns builder
        every { builder.defaultAdvisors(any<MessageChatMemoryAdvisor>()) } returns builder
        every { builder.defaultFunctions(any(), any(), any(), any()) } returns builder
        every { builder.build() } returns chatClient
        
        // Initialize GeminiService, which builds the ChatClient in its constructor
        geminiService = GeminiService(builder)
    }

    @Test
    fun `chat handles simple text response without tools`() {
        val expectedText = "Hello from Spring AI!"
        val sessionId = "session-1"
        val userMessage = "Hi there"
        
        // Mock the fluent API chain: chatClient.prompt().user().advisors().call().content()
        val requestSpecBase = mockk<ChatClient.PromptUserSpec>()
        every { chatClient.prompt() } returns requestSpecBase
        every { requestSpecBase.user(userMessage) } returns requestSpec
        
        // Mock advisors lambda
        every { requestSpec.advisors(any<(ChatClient.AdvisorSpec) -> Unit>()) } answers {
            val configurer = arg<((ChatClient.AdvisorSpec) -> Unit)>(0)
            configurer.invoke(advisorSpec)
            requestSpec
        }
        
        every { advisorSpec.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId) } returns advisorSpec
        every { requestSpec.call() } returns callResponseSpec
        every { callResponseSpec.content() } returns expectedText

        val result = geminiService.chat(sessionId, userMessage)

        assertEquals(expectedText, result)
        verify(exactly = 1) { chatClient.prompt() }
        verify(exactly = 1) { callResponseSpec.content() }
    }
}
