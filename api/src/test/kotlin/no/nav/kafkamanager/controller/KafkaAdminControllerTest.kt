package no.nav.kafkamanager.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import no.nav.kafkamanager.Application
import no.nav.kafkamanager.domain.AvailableTopic
import no.nav.kafkamanager.service.KafkaAdminService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

@WebMvcTest(
    controllers = [KafkaAdminController::class],
    excludeAutoConfiguration = [
        OAuth2ClientAutoConfiguration::class,
        OAuth2ClientWebSecurityAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = [Application::class])
class KafkaAdminControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        @MockkBean private val kafkaAdminService: KafkaAdminService,
    ) {
        @Test
        fun `available topics returns record deletion capabilities`() {
            every { kafkaAdminService.getAvailableTopics() } returns
                listOf(
                    AvailableTopic("aiven-topic", supportsRecordDeletion = true),
                    AvailableTopic("on-prem-topic", supportsRecordDeletion = false),
                )

            mockMvc
                .perform(get("/api/kafka/available-topics"))
                .andExpect(status().isOk)
                .andExpect(
                    content().json(
                        """[{"name":"aiven-topic","supportsRecordDeletion":true},{"name":"on-prem-topic","supportsRecordDeletion":false}]""",
                    ),
                )
        }

        @Test
        fun `delete records returns no content for supported topic`() {
            justRun { kafkaAdminService.deleteRecords("aiven-topic", 2, 42) }

            mockMvc
                .perform(
                    post("/api/kafka/delete-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"aiven-topic","topicPartition":2,"beforeOffset":42}"""),
                )
                .andExpect(status().isNoContent)

            verify(exactly = 1) {
                kafkaAdminService.deleteRecords("aiven-topic", 2, 42)
            }
        }

        @Test
        fun `delete records returns not implemented for unsupported topic`() {
            every { kafkaAdminService.deleteRecords("on-prem-topic", 0, 42) } throws
                ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Record deletion is not supported for this topic",
                )

            mockMvc
                .perform(
                    post("/api/kafka/delete-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"on-prem-topic","topicPartition":0,"beforeOffset":42}"""),
                )
                .andExpect(status().isNotImplemented)

            verify(exactly = 1) { kafkaAdminService.deleteRecords("on-prem-topic", 0, 42) }
        }
    }
