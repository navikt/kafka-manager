package no.nav.kafkamanager.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.kafkamanager.controller.KafkaAdminController
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.GroupListing
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec
import org.apache.kafka.clients.admin.ListGroupsResult
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
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
            every { factory.validateKafkaAdminTopic("events") } returns mockk()
            every { factory.createAivenAdminClient() } returns admin
            val listings = listOf("zeta", "other", "alpha", "empty").map { id ->
                mockk<GroupListing> {
                    every { groupId() } returns id
                }
            }
            val listGroupsResult = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns listGroupsResult
            every { listGroupsResult.all() } returns KafkaFuture.completedFuture(listings)

            val offsetsByGroup = mapOf(
                "zeta" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                "other" to mapOf(TopicPartition("another-topic", 0) to OffsetAndMetadata(2)),
                "alpha" to mapOf(TopicPartition("events", 1) to OffsetAndMetadata(8)),
                "empty" to emptyMap(),
            )
            val offsetsResult = mockk<ListConsumerGroupOffsetsResult>()
            every { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) } returns offsetsResult
            offsetsByGroup.forEach { (groupId, offsets) ->
                every { offsetsResult.partitionsToOffsetAndMetadata(groupId) } returns
                    KafkaFuture.completedFuture(offsets)
            }
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupLookupService(factory))

            // Act
            val groups = service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("events"))

            // Assert
            assertEquals(listOf("alpha", "zeta"), groups)
            verify { factory.createAivenAdminClient() }
            verify(exactly = 1) {
                admin.listConsumerGroupOffsets(
                    match<Map<String, ListConsumerGroupOffsetsSpec>> { it.keys == offsetsByGroup.keys },
                )
            }
            verify(exactly = 0) { admin.listConsumerGroupOffsets(any<String>()) }
            verify { admin.close() }
        }

        @Test
        fun `Hopper over offset-oppslag når ingen consumer-grupper finnes`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.validateKafkaAdminTopic("events") } returns mockk()
            every { factory.createAivenAdminClient() } returns admin
            val listGroupsResult = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns listGroupsResult
            every { listGroupsResult.all() } returns KafkaFuture.completedFuture(emptyList())
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupLookupService(factory))

            // Act
            val groups = service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("events"))

            // Assert
            assertEquals(emptyList<String>(), groups)
            verify(exactly = 0) { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
            verify { admin.close() }
        }

        @Test
        fun `Avviser ukjente Kafka-topics før administrasjonsklienten opprettes`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.validateKafkaAdminTopic("unknown-topic") } throws
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupLookupService(factory))

            // Act
            val exception = assertThrows<ResponseStatusException> {
                service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("unknown-topic"))
            }

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
            verify(exactly = 0) { factory.createAivenAdminClient() }
        }

        @Test
        fun `Avviser Kafka-topics uten administrasjonsstøtte`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.validateKafkaAdminTopic("on-prem-topic") } throws
                ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Kafka admin operations are not supported for this topic",
                )

            val service = KafkaConsumerGroupService(
                kafkaClientFactory = factory,
                kafkaConsumerGroupLookupService = KafkaConsumerGroupLookupService(factory),
            )

            // Act
            val exception = assertThrows<ResponseStatusException> {
                service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("on-prem-topic"))
            }

            // Assert
            assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
            assertFalse(exception.reason.isNullOrBlank())
        }
    }

    /** Tester delt caching av consumer-grupper for Kafka-topicene. */
    @Nested
    inner class CachedConsumerGroupLookup {
        @Test
        fun `Deler ett gruppeoppslag på tvers av topics`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.validateKafkaAdminTopic("events") } returns mockk()
            every { factory.validateKafkaAdminTopic("another-topic") } returns mockk()
            val eventsAdmin = adminWithNoGroups()
            val anotherTopicAdmin = adminWithNoGroups()
            every { factory.createAivenAdminClient() } returns eventsAdmin
            val listing = mockk<GroupListing> {
                every { groupId() } returns "group-a"
            }
            every { eventsAdmin.listGroups().all() } returns KafkaFuture.completedFuture(listOf(listing))
            listOf(eventsAdmin, anotherTopicAdmin).forEach { admin ->
                val offsetsResult = mockk<ListConsumerGroupOffsetsResult>()
                every { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) } returns offsetsResult
                every { offsetsResult.partitionsToOffsetAndMetadata("group-a") } returns
                    KafkaFuture.completedFuture(
                        mapOf(
                            TopicPartition("events", 0) to OffsetAndMetadata(4),
                            TopicPartition("another-topic", 0) to OffsetAndMetadata(8),
                        ),
                    )
            }

            val context = AnnotationConfigApplicationContext()
            context.register(TestCachingConfiguration::class.java)
            context.beanFactory.registerSingleton(
                "cacheManager",
                ConcurrentMapCacheManager("consumer-groups"),
            )
            context.beanFactory.registerSingleton("kafkaClientFactory", factory)
            context.register(KafkaConsumerGroupLookupService::class.java, KafkaConsumerGroupService::class.java)
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
                assertEquals(listOf("group-a"), groupsForAnotherTopic)
                verify(exactly = 1) { factory.createAivenAdminClient() }
                verify(exactly = 1) { eventsAdmin.listGroups() }
                verify(exactly = 0) { anotherTopicAdmin.listGroups() }
                verify(exactly = 1) { eventsAdmin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
                verify(exactly = 0) { anotherTopicAdmin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
                verify(exactly = 1) { eventsAdmin.close() }
                verify(exactly = 0) { anotherTopicAdmin.close() }
                verify(exactly = 2) { factory.validateKafkaAdminTopic("events") }
                verify(exactly = 1) { factory.validateKafkaAdminTopic("another-topic") }

                every { factory.validateKafkaAdminTopic("unknown-topic") } throws
                    ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
                assertThrows<ResponseStatusException> {
                    service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("unknown-topic"))
                }
                every { factory.validateKafkaAdminTopic("on-prem-topic") } throws
                    ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Kafka admin operations are not supported for this topic")
                assertThrows<ResponseStatusException> {
                    service.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("on-prem-topic"))
                }
                verify(exactly = 1) { factory.createAivenAdminClient() }
            }
        }

        @Test
        fun `Cacher ikke resultatet hvis oppslag av committed offsets feiler`() {
            // Arrange
            val admin = mockk<AdminClient>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.validateKafkaAdminTopic("events") } returns mockk()
            every { factory.createAivenAdminClient() } returns admin
            val listing = mockk<GroupListing> {
                every { groupId() } returns "group-a"
            }
            val listGroupsResult = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns listGroupsResult
            every { listGroupsResult.all() } returns KafkaFuture.completedFuture(listOf(listing))

            val failedOffsetsResult = mockk<ListConsumerGroupOffsetsResult>()
            every { failedOffsetsResult.partitionsToOffsetAndMetadata("group-a") } returns mockk {
                every { get() } throws IllegalStateException("synthetic lookup failure")
            }
            val successfulOffsetsResult = mockk<ListConsumerGroupOffsetsResult>()
            every { successfulOffsetsResult.partitionsToOffsetAndMetadata("group-a") } returns
                KafkaFuture.completedFuture(
                    mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                )
            every { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) } returnsMany
                listOf(failedOffsetsResult, successfulOffsetsResult)

            val context = AnnotationConfigApplicationContext()
            context.register(TestCachingConfiguration::class.java)
            context.beanFactory.registerSingleton(
                "cacheManager",
                ConcurrentMapCacheManager("consumer-groups"),
            )
            context.beanFactory.registerSingleton("kafkaClientFactory", factory)
            context.register(KafkaConsumerGroupLookupService::class.java, KafkaConsumerGroupService::class.java)
            context.refresh()

            context.use {
                val service = it.getBean(KafkaConsumerGroupService::class.java)
                val request = KafkaAdminController.GetConsumerGroupsRequest("events")

                // Act
                val exception = assertThrows<IllegalStateException> {
                    service.getConsumerGroups(request)
                }
                val groups = service.getConsumerGroups(request)

                // Assert
                assertEquals("synthetic lookup failure", exception.message)
                assertEquals(listOf("group-a"), groups)
                verify(exactly = 2) { factory.createAivenAdminClient() }
                verify(exactly = 2) { admin.listGroups() }
                verify(exactly = 2) { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
                verify(exactly = 2) { admin.close() }
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
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupLookupService(factory))

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
            val service = KafkaConsumerGroupService(factory, KafkaConsumerGroupLookupService(factory))

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
