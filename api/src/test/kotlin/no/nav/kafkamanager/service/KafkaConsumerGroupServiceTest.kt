package no.nav.kafkamanager.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.kafkamanager.controller.KafkaAdminController
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.GroupListing
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult
import org.apache.kafka.clients.admin.ListGroupsResult
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Tester oppslag og endring av consumer-grupper og deres committed offsets. */
class KafkaConsumerGroupServiceTest {
    /** Tester filtrering av consumer-grupper for det forespurte Kafka-topicet. */
    @Nested
    inner class ConsumerGroupLookup {
        @Test
        fun `Filtrerer på Kafka-topicet sorterer treffene og utelater grupper med offset-feil`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("events") } returns admin
            val listings = listOf("zeta", "other", "alpha", "broken").map { id ->
                mockk<GroupListing> {
                    every { groupId() } returns id
                }
            }
            val listGroupsResult = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns listGroupsResult
            every { listGroupsResult.all() } returns KafkaFuture.completedFuture(listings)

            listOf(
                "zeta" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                "other" to mapOf(TopicPartition("another-topic", 0) to OffsetAndMetadata(2)),
                "alpha" to mapOf(TopicPartition("events", 1) to OffsetAndMetadata(8)),
            ).forEach { (group, offsets) ->
                val offsetsResult = mockk<ListConsumerGroupOffsetsResult>()
                every { admin.listConsumerGroupOffsets(group) } returns offsetsResult
                every { offsetsResult.partitionsToOffsetAndMetadata() } returns
                    KafkaFuture.completedFuture(offsets)
            }
            val failedResult = mockk<ListConsumerGroupOffsetsResult>()
            every { admin.listConsumerGroupOffsets("broken") } returns failedResult
            every { failedResult.partitionsToOffsetAndMetadata() } returns mockk {
                every { get() } throws IllegalStateException("synthetic lookup failure")
            }
            val service = KafkaConsumerGroupService(factory)

            // Act
            val groups = service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("events"))

            // Assert
            assertEquals(listOf("alpha", "zeta"), groups)
            verify { factory.createKafkaAdminClientForTopic("events") }
        }

        @Test
        fun `Avviser ukjente Kafka-topics før administrasjonsklienten opprettes`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("unknown-topic") } throws
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
            val service = KafkaConsumerGroupService(factory)

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("unknown-topic"))
            }

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
            verify(exactly = 1) { factory.createKafkaAdminClientForTopic("unknown-topic") }
        }

        @Test
        fun `Avviser Kafka-topics uten administrasjonsstøtte`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("on-prem-topic") } throws
                ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Kafka admin operations are not supported for this topic",
                )
            val service = KafkaConsumerGroupService(factory)

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("on-prem-topic"))
            }

            // Assert
            assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
            assertFalse(exception.reason.isNullOrBlank())
        }
    }

    /** Tester oppslag av committed offsets for partisjonene i et Kafka-topic. */
    @Nested
    inner class ConsumerOffsetLookup {
        @Test
        fun `Returnerer committed offsets bare for partisjoner med lagret offset`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic("group-a", "events") } returns consumer
            every { consumer.partitionsFor("events") } returns listOf(
                PartitionInfo("events", 0, mockk<Node>(), emptyArray(), emptyArray()),
                PartitionInfo("events", 1, mockk<Node>(), emptyArray(), emptyArray()),
            )
            val existingPartition = TopicPartition("events", 0)
            val missingPartition = TopicPartition("events", 1)
            val committed = mutableMapOf<TopicPartition, OffsetAndMetadata?>(
                existingPartition to OffsetAndMetadata(17),
                missingPartition to null,
            )
            every { consumer.committed(setOf(existingPartition, missingPartition)) } returns committed
            val service = KafkaConsumerGroupService(factory)

            // Act
            val offsets = service.getConsumerOffsets(
                KafkaAdminController.GetConsumerOffsetsRequest("group-a", "events"),
            )

            // Assert
            assertEquals(mapOf(existingPartition to OffsetAndMetadata(17)), offsets)
            verify { consumer.close() }
        }
    }

    /** Tester overskriving av committed offset. */
    @Nested
    inner class ConsumerOffsetUpdate {
        @Test
        fun `Tildeler partisjonen søker til offsetten og committer endringen`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic("group-a", "events") } returns consumer
            val partition = TopicPartition("events", 3)
            val service = KafkaConsumerGroupService(factory)

            // Act
            service.setConsumerOffset(
                KafkaAdminController.SetConsumerOffsetRequest("group-a", "events", 3, 29),
            )

            // Assert
            verify { consumer.assign(listOf(partition)) }
            verify { consumer.seek(partition, 29) }
            verify { consumer.commitSync() }
            verify { consumer.close() }
        }
    }
}
