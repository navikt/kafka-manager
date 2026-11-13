package no.nav.kafkamanager.security

import com.ninjasquad.springmockk.MockkBean
import no.nav.kafkamanager.config.SecurityConfig
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.service.KafkaConsumerGroupService
import no.nav.kafkamanager.service.KafkaRecordReaderService
import no.nav.kafkamanager.service.KafkaTopicOperationsService
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Tester autentisering av beskyttede applikasjonsendepunkter. */
@EnableWebSecurity
@WebMvcTest(
    controllers = [KafkaAdminController::class],
    properties = [
        "app.oauth2.callback-path=/oauth2/callback",
        "spring.security.oauth2.client.registration.azure.client-id=test-client",
    ],
)
@Import(SecurityConfig::class, OAuthTestClientConfiguration::class)
class AuthenticationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        @Suppress("UNUSED_PARAMETER") @MockkBean private val kafkaTopicOperationsService: KafkaTopicOperationsService,
        @Suppress("UNUSED_PARAMETER") @MockkBean private val kafkaConsumerGroupService: KafkaConsumerGroupService,
        @Suppress("UNUSED_PARAMETER") @MockkBean private val kafkaRecordReaderService: KafkaRecordReaderService,
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
        fun `Krever OAuth2-login på alle beskyttede endepunkter`(
            method: String,
            path: String,
        ) {
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
