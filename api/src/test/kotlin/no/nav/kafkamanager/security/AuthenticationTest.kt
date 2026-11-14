package no.nav.kafkamanager.security

import com.ninjasquad.springmockk.MockkBean
import io.mockk.justRun
import io.mockk.verify
import jakarta.servlet.http.Cookie
import no.nav.kafkamanager.config.SecurityConfig
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.service.KafkaConsumerGroupService
import no.nav.kafkamanager.service.KafkaRecordReaderService
import no.nav.kafkamanager.service.KafkaTopicOperationsService
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
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
            val csrfCookie = if (method.equals(HttpMethod.POST.name(), ignoreCase = true)) {
                getCsrfCookie()
            } else {
                null
            }

            val securedRequest = request(HttpMethod.valueOf(method), path).apply {
                csrfCookie?.let {
                    cookie(it)
                    header("X-XSRF-TOKEN", it.value)
                }
            }

            // Act
            val result = mockMvc.perform(securedRequest)

            // Assert
            result
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/oauth2/authorization/azure"))
        }

        @ParameterizedTest(name = "Avviser {0} uten CSRF-token")
        @CsvSource(
            "/api/kafka/set-consumer-offset",
            "/api/kafka/delete-records",
        )
        fun `Avviser POST-kall uten CSRF-token`(path: String) {
            mockMvc
                .perform(request(HttpMethod.POST, path))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `Godtar offset-endring med gyldig CSRF-cookie og header`() {
            val csrfCookie = getCsrfCookie()
            val offsetRequest = KafkaAdminController.SetConsumerOffsetRequest("test-group", "test-topic", 0, 42)
            justRun { kafkaConsumerGroupService.setConsumerOffset(offsetRequest) }

            mockMvc
                .perform(setOffsetRequest(csrfCookie, csrfCookie.value))
                .andExpect(status().isOk)

            verify(exactly = 1) { kafkaConsumerGroupService.setConsumerOffset(offsetRequest) }
        }

        @Test
        fun `Avviser autentisert offset-endring uten CSRF-header`() {
            val csrfCookie = getCsrfCookie()

            mockMvc
                .perform(setOffsetRequest(csrfCookie, null))
                .andExpect(status().isForbidden)

            verify(exactly = 0) { kafkaConsumerGroupService.setConsumerOffset(any()) }
        }

        @Test
        fun `Avviser autentisert offset-endring med feil CSRF-header`() {
            val csrfCookie = getCsrfCookie()

            mockMvc
                .perform(setOffsetRequest(csrfCookie, "not-the-cookie-token"))
                .andExpect(status().isForbidden)

            verify(exactly = 0) { kafkaConsumerGroupService.setConsumerOffset(any()) }
        }

        @Test
        fun `Eksponerer CSRF-cookie som frontend kan lese`() {
            val csrfCookie = getCsrfCookie()

            assertFalse(csrfCookie.isHttpOnly)
            assertTrue(csrfCookie.value.isNotBlank())
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

        private fun getCsrfCookie(): Cookie = requireNotNull(
            mockMvc
                .perform(get("/api/kafka/available-topics"))
                .andReturn()
                .response
                .getCookie("XSRF-TOKEN"),
        )

        private fun setOffsetRequest(
            csrfCookie: Cookie,
            csrfHeader: String?,
        ): MockHttpServletRequestBuilder = request(HttpMethod.POST, "/api/kafka/set-consumer-offset")
            .session(authenticatedSession())
            .cookie(csrfCookie)
            .apply {
                csrfHeader?.let { header("X-XSRF-TOKEN", it) }
            }.contentType(MediaType.APPLICATION_JSON)
            .content("""{"groupId":"test-group","topicName":"test-topic","topicPartition":0,"offset":42}""")

        private fun authenticatedSession(): MockHttpSession {
            val securityContext = SecurityContextHolder.createEmptyContext()
            securityContext.authentication = UsernamePasswordAuthenticationToken(
                "test-user",
                "test-credentials",
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            )
            return MockHttpSession().apply {
                setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext)
            }
        }
    }
