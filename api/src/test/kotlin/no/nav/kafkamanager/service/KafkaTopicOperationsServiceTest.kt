package no.nav.kafkamanager.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.AvailableTopic
import no.nav.kafkamanager.domain.DeserializerType
import no.nav.kafkamanager.domain.TopicConfig
import no.nav.kafkamanager.domain.TopicLocation
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.DeleteRecordsResult
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Tester oversikt over Kafka-topics, offsettoppslag og sletting av poster. */
class KafkaTopicOperationsServiceTest {
    /** Tester oversikten over Kafka-topics. */
    @Nested
    inner class AvailableTopics {
        @Test
        fun `Viser alle konfigurerte Kafka-topics og om sletting støttes`() {
            // Arrange
            val service = service()

            // Act
            val topics = service.getAvailableTopics()

            // Assert
            assertEquals(
                listOf(
                    AvailableTopic("aiven-topic", supportsRecordDeletion = true),
                    AvailableTopic("on-prem-topic", supportsRecordDeletion = false),
                ),
                topics,
            )
        }
    }

    /** Tester oppslag av offsett for én partisjon eller alle partisjoner. */
    @Nested
    inner class LastRecordOffsets {
        @Test
        fun `Søker til slutten av den valgte partisjonen og returnerer posisjonen`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "aiven-topic") } returns consumer
            val partition = TopicPartition("aiven-topic", 4)
            every { consumer.position(partition) } returns 91L
            val service = service(factory)

            // Act
            val offset = service.getLastRecordOffset(
                KafkaAdminController.GetLastRecordOffsetRequest("aiven-topic", 4),
            )

            // Assert
            assertEquals(91L, offset)
            verify { consumer.assign(setOf(partition)) }
            verify { consumer.seekToEnd(setOf(partition)) }
            verify { consumer.close() }
        }

        @Test
        fun `Returnerer offsetter sortert etter partisjonsnummer`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "aiven-topic") } returns consumer
            every { consumer.partitionsFor("aiven-topic") } returns listOf(
                PartitionInfo("aiven-topic", 2, null, emptyArray(), emptyArray()),
                PartitionInfo("aiven-topic", 0, null, emptyArray(), emptyArray()),
                PartitionInfo("aiven-topic", 1, null, emptyArray(), emptyArray()),
            )
            val p0 = TopicPartition("aiven-topic", 0)
            val p1 = TopicPartition("aiven-topic", 1)
            val p2 = TopicPartition("aiven-topic", 2)
            every { consumer.endOffsets(listOf(p2, p0, p1)) } returns mapOf(p2 to 30L, p0 to 10L, p1 to 20L)
            val service = service(factory)

            // Act
            val offsets = service.getLastRecordOffsets(
                KafkaAdminController.GetLastRecordOffsetsRequest("aiven-topic"),
            )

            // Assert
            assertEquals(
                listOf(
                    KafkaAdminController.TopicWithOffset("aiven-topic", 0, 10),
                    KafkaAdminController.TopicWithOffset("aiven-topic", 1, 20),
                    KafkaAdminController.TopicWithOffset("aiven-topic", 2, 30),
                ),
                offsets,
            )
            verify { consumer.close() }
        }

        @Test
        fun `Returnerer en tom liste når Kafka-topicet ikke har partisjoner`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "aiven-topic") } returns consumer
            every { consumer.partitionsFor("aiven-topic") } returns emptyList()
            val service = service(factory)

            // Act
            val offsets = service.getLastRecordOffsets(
                KafkaAdminController.GetLastRecordOffsetsRequest("aiven-topic"),
            )

            // Assert
            assertTrue(offsets.isEmpty())
            verify(exactly = 0) { consumer.endOffsets(any()) }
            verify { consumer.close() }
        }

        @Test
        fun `Avviser ukjent Kafka-topic ved oppslag av én partisjon`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "unknown-topic") } throws
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
            val service = service(factory)

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.getLastRecordOffset(KafkaAdminController.GetLastRecordOffsetRequest("unknown-topic", 0))
            }

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }
    }

    /** Tester sletting av poster før en oppgitt offsett. */
    @Nested
    inner class RecordDeletion {
        @Test
        fun `Sletter poster før oppgitt offsett for riktig partisjon`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("aiven-topic") } returns admin
            val deleteResult = mockk<DeleteRecordsResult>()
            every { admin.deleteRecords(any()) } returns deleteResult
            every { deleteResult.all() } returns KafkaFuture.completedFuture(null)
            val requestedDeletion = slot<Map<TopicPartition, RecordsToDelete>>()
            val service = service(factory)

            // Act
            service.deleteRecords("aiven-topic", 2, 25)

            // Assert
            verify { admin.deleteRecords(capture(requestedDeletion)) }
            assertEquals(
                mapOf(TopicPartition("aiven-topic", 2) to RecordsToDelete.beforeOffset(25)),
                requestedDeletion.captured,
            )
            verify { admin.close() }
        }

        @Test
        fun `Returnerer intern serverfeil når Kafka feiler under sletting`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("aiven-topic") } returns admin
            val deleteResult = mockk<DeleteRecordsResult>()
            every { admin.deleteRecords(any()) } returns deleteResult
            every { deleteResult.all() } returns mockk<KafkaFuture<Void>> {
                every { get() } throws IllegalStateException("synthetic broker failure")
            }
            val service = service(factory)

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.deleteRecords("aiven-topic", 2, 25)
            }

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.statusCode)
            assertTrue(exception.reason.orEmpty().contains("Failed to delete records"))
        }

        @Test
        fun `Avviser sletting på Kafka-topics uten administrasjonsstøtte`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("on-prem-topic") } throws
                ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Kafka admin operations are not supported for this topic",
                )
            val service = service(factory)

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.deleteRecords("on-prem-topic", 0, 1)
            }

            // Assert
            assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
        }
    }

    /** Lager topic-operasjonstjenesten med testkonfigurasjon og valgt klientfabrikk. */
    private fun service(factory: KafkaClientFactory = mockk()) = KafkaTopicOperationsService(factory, appConfig)

    private companion object {
        val appConfig = AppConfig(
            topics = listOf(
                TopicConfig("aiven-topic", TopicLocation.AIVEN, DeserializerType.STRING, DeserializerType.STRING, null),
                TopicConfig(
                    "on-prem-topic",
                    TopicLocation.ON_PREM,
                    DeserializerType.STRING,
                    DeserializerType.STRING,
                    null,
                ),
            ),
        )
    }
}
