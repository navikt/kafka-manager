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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Tester oppslag og endring av consumer-grupper og deres committed offsets. */
class KafkaConsumerGroupServiceTest {
    /** Tester filtrering av consumer-grupper for det forespurte Kafka-topicet. */
    @Nested
    inner class ConsumerGroupLookup {
        @Test
        fun `Filtrerer på Kafka-topicet og sorterer treffene`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("events") } returns admin
            val listings = listOf("zeta", "other", "alpha").map { id ->
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
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupCache())

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
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupCache())

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
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupCache())

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("on-prem-topic"))
            }

            // Assert
            assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
            assertFalse(exception.reason.isNullOrBlank())
        }
    }

    /** Tester caching av consumer-grupper per Kafka-topic. */
    @Nested
    inner class CachedConsumerGroupLookup {
        @Test
        fun `Cacher consumer-grupper separat per topic`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            val eventsAdmin = adminWithNoGroups()
            val anotherTopicAdmin = adminWithNoGroups()
            every { factory.createKafkaAdminClientForTopic("events") } returns eventsAdmin
            every { factory.createKafkaAdminClientForTopic("another-topic") } returns anotherTopicAdmin
            val listing = mockk<GroupListing> {
                every { groupId() } returns "group-a"
            }
            every { eventsAdmin.listGroups().all() } returns KafkaFuture.completedFuture(listOf(listing))
            listOf(eventsAdmin, anotherTopicAdmin).forEach { admin ->
                val offsetsResult = mockk<ListConsumerGroupOffsetsResult>()
                every { admin.listConsumerGroupOffsets("group-a") } returns offsetsResult
                every { offsetsResult.partitionsToOffsetAndMetadata() } returns
                    KafkaFuture.completedFuture(
                        mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                    )
            }

            val context = AnnotationConfigApplicationContext()
            context.register(TestCachingConfiguration::class.java)
            context.beanFactory.registerSingleton(
                "cacheManager",
                ConcurrentMapCacheManager("consumer-groups", "consumer-group-ids"),
            )
            context.beanFactory.registerSingleton("kafkaClientFactory", factory)
            context.register(KafkaConsumerGroupCache::class.java, KafkaConsumerGroupService::class.java)
            context.refresh()

            context.use {
                val service = it.getBean(KafkaConsumerGroupService::class.java)
                val eventsRequest = KafkaAdminController.GetConsumerGroupsRequest("events")

                // Act
                val groups = service.getConsumerGroups(eventsRequest)
                val cachedGroups = service.getConsumerGroups(eventsRequest)
                val groupsForAnotherTopic = service.getConsumerGroups(
                    KafkaAdminController.GetConsumerGroupsRequest("another-topic"),
                )

                // Assert
                assertEquals(listOf("group-a"), groups)
                assertEquals(groups, cachedGroups)
                assertEquals(emptyList<String>(), groupsForAnotherTopic)
                verify(exactly = 1) { factory.createKafkaAdminClientForTopic("events") }
                verify(exactly = 1) { factory.createKafkaAdminClientForTopic("another-topic") }
                verify(exactly = 1) { eventsAdmin.listGroups() }
                verify(exactly = 0) { anotherTopicAdmin.listGroups() }
                verify(exactly = 1) { eventsAdmin.listConsumerGroupOffsets("group-a") }
                verify(exactly = 1) { anotherTopicAdmin.listConsumerGroupOffsets("group-a") }
            }
        }

        @Test
        fun `Cacher ikke resultatet hvis oppslag av committed offsets feiler`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaAdminClientForTopic("events") } returns admin
            val listing = mockk<GroupListing> {
                every { groupId() } returns "group-a"
            }
            val listGroupsResult = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns listGroupsResult
            every { listGroupsResult.all() } returns KafkaFuture.completedFuture(listOf(listing))

            val failedOffsetsResult = mockk<ListConsumerGroupOffsetsResult>()
            every { failedOffsetsResult.partitionsToOffsetAndMetadata() } returns mockk {
                every { get() } throws IllegalStateException("synthetic lookup failure")
            }
            val successfulOffsetsResult = mockk<ListConsumerGroupOffsetsResult>()
            every { successfulOffsetsResult.partitionsToOffsetAndMetadata() } returns
                KafkaFuture.completedFuture(
                    mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                )
            every { admin.listConsumerGroupOffsets("group-a") } returnsMany
                listOf(failedOffsetsResult, successfulOffsetsResult)

            val context = AnnotationConfigApplicationContext()
            context.register(TestCachingConfiguration::class.java)
            context.beanFactory.registerSingleton(
                "cacheManager",
                ConcurrentMapCacheManager("consumer-groups", "consumer-group-ids"),
            )
            context.beanFactory.registerSingleton("kafkaClientFactory", factory)
            context.register(KafkaConsumerGroupCache::class.java, KafkaConsumerGroupService::class.java)
            context.refresh()

            context.use {
                val service = it.getBean(KafkaConsumerGroupService::class.java)
                val request = KafkaAdminController.GetConsumerGroupsRequest("events")

                // Act
                val exception = assertThrows(IllegalStateException::class.java) {
                    service.getConsumerGroups(request)
                }
                val groups = service.getConsumerGroups(request)

                // Assert
                assertEquals("synthetic lookup failure", exception.message)
                assertEquals(listOf("group-a"), groups)
                verify(exactly = 2) { factory.createKafkaAdminClientForTopic("events") }
                verify(exactly = 1) { admin.listGroups() }
                verify(exactly = 2) { admin.listConsumerGroupOffsets("group-a") }
            }
        }

        private fun adminWithNoGroups(): AdminClient {
            val admin = mockk<AdminClient>(relaxed = true)
            val result = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns result
            every { result.all() } returns KafkaFuture.completedFuture(emptyList())
            return admin
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableCaching
    class TestCachingConfiguration

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
            val committed = mutableMapOf(
                existingPartition to OffsetAndMetadata(17),
                missingPartition to null,
            )
            every { consumer.committed(setOf(existingPartition, missingPartition)) } returns committed
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupCache())

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
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupCache())

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
