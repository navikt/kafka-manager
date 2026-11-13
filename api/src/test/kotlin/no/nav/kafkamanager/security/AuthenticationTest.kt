package no.nav.kafkamanager.security

import no.nav.kafkamanager.Application
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Tester autentisering av beskyttede applikasjonsendepunkter. */
@SpringBootTest(
    classes = [Application::class],
    properties = [
        "app.env.app-config-json={\"topics\":[]}",
        "spring.security.oauth2.client.registration.azure.client-id=test-client",
    ],
)
@AutoConfigureMockMvc
@Import(OAuthTestClientConfiguration::class)
class AuthenticationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
    @ParameterizedTest(name = "{0} {1} krever OAuth2-login")
    @CsvSource(
        "GET, /api/kafka/available-topics",
        "POST, /api/kafka/read-topic",
        "POST, /api/kafka/get-consumer-offsets",
        "POST, /api/kafka/get-last-record-offset",
        "POST, /api/kafka/get-last-record-offsets",
        "POST, /api/kafka/set-consumer-offset",
        "POST, /api/kafka/get-consumer-groups",
        "POST, /api/kafka/delete-records",
        "GET, /",
        "GET, /index.html",
    )
    fun `Krever OAuth2-login på alle beskyttede endepunkter`(method: String, path: String) {
        // Arrange
        val securedRequest = request(HttpMethod.valueOf(method), path)

        // Act
        val result = mockMvc.perform(securedRequest)

        // Assert
        result
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/oauth2/authorization/azure"))
    }

    @Test
    fun `Starter OAuth2-login med klientregistreringen`() {
        // Arrange
        val authorizationRequest = get("/oauth2/authorization/azure")

        // Act
        val result = mockMvc.perform(authorizationRequest)

        // Assert
        result
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string("Location", startsWith("https://idp.invalid/authorize")))
    }
}
