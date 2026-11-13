package no.nav.kafkamanager.actuator

import no.nav.kafkamanager.Application
import no.nav.kafkamanager.security.OAuthTestClientConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.resttestclient.getForEntity
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

/** Tester anonym tilgang til Kafka Managers Actuator-endepunkter. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [Application::class],
    properties = [
        "app.env.app-config-json={\"topics\":[]}",
        "spring.security.oauth2.client.registration.azure.client-id=test-client",
    ],
)
@Import(OAuthTestClientConfiguration::class)
@AutoConfigureTestRestTemplate
class ActuatorTest
    @Autowired
    constructor(
        @LocalManagementPort private val managementPort: Int,
        private val restTemplate: TestRestTemplate,
    ) {
        @Test
        fun `Gir OK for anonym forespørsel til liveness`() {
            // Arrange
            val uri = UriComponentsBuilder
                .fromUriString("http://localhost:{port}/internal/health/liveness")
                .buildAndExpand(managementPort)
                .toUri()

            // Act
            val response = restTemplate.getForEntity<String>(uri)

            // Assert
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("""{"status":"UP"}""", response.body)
        }

        @Test
        fun `Gir OK for anonym forespørsel til readiness`() {
            // Arrange
            val uri = UriComponentsBuilder
                .fromUriString("http://localhost:{port}/internal/health/readiness")
                .buildAndExpand(managementPort)
                .toUri()

            // Act
            val response = restTemplate.getForEntity<String>(uri)

            // Assert
            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals("""{"status":"UP"}""", response.body)
        }

        @Test
        fun `Gir OK for anonym forespørsel til Prometheus`() {
            // Arrange
            val uri = UriComponentsBuilder
                .fromUriString("http://localhost:{port}/internal/prometheus")
                .buildAndExpand(managementPort)
                .toUri()

            // Act
            val response = restTemplate.getForEntity<String>(uri)

            // Assert
            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }
