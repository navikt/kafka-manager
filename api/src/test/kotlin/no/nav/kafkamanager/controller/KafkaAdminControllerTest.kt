package no.nav.kafkamanager.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import no.nav.kafkamanager.Application
import no.nav.kafkamanager.domain.AvailableTopic
import no.nav.kafkamanager.service.KafkaConsumerGroupService
import no.nav.kafkamanager.service.KafkaRecordReaderService
import no.nav.kafkamanager.service.KafkaTopicOperationsService
import org.junit.jupiter.api.Nested
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

/**
 * Tester at Kafka-administrasjonsendepunktene videresender forespørsler til riktige tjenester.
 */
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
        @MockkBean private val kafkaTopicOperationsService: KafkaTopicOperationsService,
        @MockkBean private val kafkaConsumerGroupService: KafkaConsumerGroupService,
        @MockkBean private val kafkaRecordReaderService: KafkaRecordReaderService,
    ) {
        /** Tester endepunktene for tilgjengelige Kafka-topics og topic-operasjoner. */
        @Nested
        inner class TopicOperationsTests {
            @Test
            fun `Henter tilgjengelige Kafka-topics med støtte for sletting av poster`() {
                // Arrange
                every { kafkaTopicOperationsService.getAvailableTopics() } returns
                    listOf(
                        AvailableTopic("aiven-topic", supportsRecordDeletion = true),
                        AvailableTopic("on-prem-topic", supportsRecordDeletion = false),
                    )

                // Act
                val result = mockMvc.perform(get("/api/kafka/available-topics"))

                // Assert
                result
                    .andExpect(status().isOk)
                    .andExpect(
                        content().json(
                            """[{"name":"aiven-topic","supportsRecordDeletion":true},{"name":"on-prem-topic","supportsRecordDeletion":false}]""",
                        ),
                    )

                verify(exactly = 1) { kafkaTopicOperationsService.getAvailableTopics() }
            }

            @Test
            fun `Sletter poster for et støttet Kafka-topic og videresender parametrene til tjenesten`() {
                // Arrange
                justRun { kafkaTopicOperationsService.deleteRecords("aiven-topic", 2, 42) }

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/delete-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"aiven-topic","topicPartition":2,"beforeOffset":42}"""),
                )

                // Assert
                result.andExpect(status().isNoContent)
                verify(exactly = 1) {
                    kafkaTopicOperationsService.deleteRecords("aiven-topic", 2, 42)
                }
            }

            @Test
            fun `Returnerer ikke implementert når Kafka-topicet ikke støtter sletting av poster`() {
                // Arrange
                every { kafkaTopicOperationsService.deleteRecords("on-prem-topic", 0, 42) } throws
                    ResponseStatusException(
                        HttpStatus.NOT_IMPLEMENTED,
                        "Kafka admin operations are not supported for this topic",
                    )

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/delete-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"on-prem-topic","topicPartition":0,"beforeOffset":42}"""),
                )

                // Assert
                result.andExpect(status().isNotImplemented)
                verify(exactly = 1) { kafkaTopicOperationsService.deleteRecords("on-prem-topic", 0, 42) }
            }

            @Test
            fun `Henter siste postoffset for alle partisjoner fra topic-operasjonstjenesten`() {
                // Arrange
                val offsets = listOf(
                    KafkaAdminController.TopicWithOffset("aiven-topic", 0, 42),
                    KafkaAdminController.TopicWithOffset("aiven-topic", 1, 84),
                )
                val request = KafkaAdminController.GetLastRecordOffsetsRequest("aiven-topic")
                every { kafkaTopicOperationsService.getLastRecordOffsets(request) } returns offsets

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/get-last-record-offsets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"aiven-topic"}"""),
                )

                // Assert
                result
                    .andExpect(status().isOk)
                    .andExpect(
                        content().json(
                            """[{"topicName":"aiven-topic","topicPartition":0,"offset":42},{"topicName":"aiven-topic","topicPartition":1,"offset":84}]""",
                        ),
                    )
                verify(exactly = 1) { kafkaTopicOperationsService.getLastRecordOffsets(request) }
            }

            @Test
            fun `Henter siste postoffset for én partisjon fra topic-operasjonstjenesten`() {
                // Arrange
                val request = KafkaAdminController.GetLastRecordOffsetRequest("aiven-topic", 0)
                every { kafkaTopicOperationsService.getLastRecordOffset(request) } returns 42

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/get-last-record-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"aiven-topic","topicPartition":0}"""),
                )

                // Assert
                result
                    .andExpect(status().isOk)
                    .andExpect(content().json("""{"latestRecordOffset":42}"""))
                verify(exactly = 1) { kafkaTopicOperationsService.getLastRecordOffset(request) }
            }
        }

        /** Tester endepunktene for consumer-grupper og committed offsets. */
        @Nested
        inner class ConsumerGroupTests {
            @Test
            fun `Henter consumer-grupper fra consumer-gruppetjenesten`() {
                // Arrange
                val request = KafkaAdminController.GetConsumerGroupsRequest("aiven-topic")
                every { kafkaConsumerGroupService.getConsumerGroups(request) } returns listOf("group")

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/get-consumer-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"topicName":"aiven-topic"}"""),
                )

                // Assert
                result
                    .andExpect(status().isOk)
                    .andExpect(content().json("""["group"]"""))
                verify(exactly = 1) { kafkaConsumerGroupService.getConsumerGroups(request) }
            }

            @Test
            fun `Henter committed offsets fra consumer-gruppetjenesten`() {
                // Arrange
                val request = KafkaAdminController.GetConsumerOffsetsRequest("group", "aiven-topic")
                every { kafkaConsumerGroupService.getConsumerOffsets(request) } returns emptyMap()

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/get-consumer-offsets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"groupId":"group","topicName":"aiven-topic"}"""),
                )

                // Assert
                result
                    .andExpect(status().isOk)
                    .andExpect(content().json("[]"))
                verify(exactly = 1) { kafkaConsumerGroupService.getConsumerOffsets(request) }
            }

            @Test
            fun `Setter en consumer-offset ved å videresende forespørselen til tjenesten`() {
                // Arrange
                val request = KafkaAdminController.SetConsumerOffsetRequest("group", "aiven-topic", 0, 42)
                justRun { kafkaConsumerGroupService.setConsumerOffset(request) }

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/set-consumer-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"groupId":"group","topicName":"aiven-topic","topicPartition":0,"offset":42}"""),
                )

                // Assert
                result.andExpect(status().isOk)
                verify(exactly = 1) { kafkaConsumerGroupService.setConsumerOffset(request) }
            }
        }

        /** Tester lesing av poster fra et Kafka-topic. */
        @Nested
        inner class ReadTopicTests {
            @Test
            fun `Leser poster og videresender forespørselen til lesertjenesten`() {
                // Arrange
                val request = KafkaAdminController.ReadTopicRequest(
                    topicName = "aiven-topic",
                    topicAllPartitions = false,
                    topicPartition = 0,
                    maxRecords = 10,
                    fromOffset = 0,
                    filter = null,
                )
                every { kafkaRecordReaderService.readTopic(request) } returns emptyList()

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/read-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"topicName":"aiven-topic","topicAllPartitions":false,"topicPartition":0,"maxRecords":10,"fromOffset":0,"filter":null}""",
                        ),
                )

                // Assert
                result
                    .andExpect(status().isOk)
                    .andExpect(content().json("[]"))
                verify(exactly = 1) { kafkaRecordReaderService.readTopic(request) }
            }

            @Test
            fun `Avviser ugyldig antall poster uten å kalle lesertjenesten`() {
                // Arrange
                val request =
                    """{"topicName":"aiven-topic","topicAllPartitions":false,"topicPartition":0,"maxRecords":101,"fromOffset":0,"filter":null}"""

                // Act
                val result = mockMvc.perform(
                    post("/api/kafka/read-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request),
                )

                // Assert
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { kafkaRecordReaderService.readTopic(any()) }
            }
        }
    }
