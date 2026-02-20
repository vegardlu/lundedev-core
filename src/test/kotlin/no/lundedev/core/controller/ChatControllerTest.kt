package no.lundedev.core.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.lundedev.core.service.GeminiService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ChatController::class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var geminiService: GeminiService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @TestConfiguration
    class Config {
        @Bean
        fun geminiService() = mockk<GeminiService>()
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `chat should return response from GeminiService`() {
        val request = ChatRequest(message = "Hello, world!")
        val expectedResponse = "Hello from Gemini"

        every { geminiService.chat(any(), "Hello, world!") } returns expectedResponse

        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("\$.response").value(expectedResponse))
    }
}
