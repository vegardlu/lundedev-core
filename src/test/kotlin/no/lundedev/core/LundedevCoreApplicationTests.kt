package no.lundedev.core

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = [
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret"
])
class LundedevCoreApplicationTests {

    @Test
    fun contextLoads() {
    }

}
