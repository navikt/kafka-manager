package no.nav.kafkamanager.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.GroupListing
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec
import org.apache.kafka.clients.admin.ListGroupsResult
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.GroupAuthorizationException
import org.apache.kafka.common.errors.GroupIdNotFoundException
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@SpringJUnitConfig(KafkaConsumerGroupCacheTest.TestCachingConfiguration::class)
class KafkaConsumerGroupCacheTest
    @Autowired
    constructor(
        private val cache: KafkaConsumerGroupCache,
        private val cacheManager: CaffeineCacheManager,
        private val ticker: AtomicLong,
    ) {
        @BeforeEach
        fun resetCache() {
            requireNotNull(cacheManager.getCache("consumer-groups")).clear()
            ticker.set(0)
        }

        @Test
        fun `Cacher sorterte grupper per topic på tvers av administrasjonsklienter`() {
            // Arrange
            val admin = mockk<Admin>()
            val anotherAdmin = mockk<Admin>()
            val result = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns result
            every { result.all() } returns KafkaFuture.completedFuture(groupListings("group-a", "group-b"))
            mockOffsets(
                admin,
                mapOf(
                    "group-b" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                    "group-a" to mapOf(
                        TopicPartition("events", 0) to OffsetAndMetadata(8),
                        TopicPartition("events", 1) to OffsetAndMetadata(12),
                        TopicPartition("another-topic", 0) to OffsetAndMetadata(16),
                    ),
                ),
            )

            // Act
            val groups = cache.getAivenGroupsByTopic(admin)
            val cachedGroups = cache.getAivenGroupsByTopic(admin)
            val groupsFromAnotherAdmin = cache.getAivenGroupsByTopic(anotherAdmin)

            // Assert
            assertEquals(
                mapOf("events" to listOf("group-a", "group-b"), "another-topic" to listOf("group-a")),
                groups,
            )
            assertEquals(groups, cachedGroups)
            assertEquals(groups, groupsFromAnotherAdmin)
            verify(exactly = 1) { admin.listGroups() }
            verify(exactly = 0) { anotherAdmin.listGroups() }
            verify(exactly = 1) { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
        }

        @Test
        fun `Cacher en tom gruppeliste`() {
            // Arrange
            val admin = mockk<Admin>()
            val result = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns result
            every { result.all() } returns KafkaFuture.completedFuture(emptyList())

            // Act
            val groups = cache.getAivenGroupsByTopic(admin)
            val cachedGroups = cache.getAivenGroupsByTopic(admin)

            // Assert
            assertEquals(emptyMap<String, List<String>>(), groups)
            assertEquals(groups, cachedGroups)
            verify(exactly = 1) { admin.listGroups() }
            verify(exactly = 0) { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
        }

        @Test
        fun `Propagerer oppslagsfeil uten å cache dem`() {
            // Arrange
            val admin = mockk<Admin>()
            val result = mockk<ListGroupsResult>()
            val failedFuture = mockk<KafkaFuture<Collection<GroupListing>>>()
            val failure = IllegalStateException("synthetic lookup failure")
            every { admin.listGroups() } returns result
            every { failedFuture.get() } throws failure
            every { result.all() } returnsMany listOf(
                failedFuture,
                KafkaFuture.completedFuture(groupListings("group-a")),
            )
            mockOffsets(admin, mapOf("group-a" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4))))

            // Act
            val exception = assertThrows(IllegalStateException::class.java) {
                cache.getAivenGroupsByTopic(admin)
            }
            val groups = cache.getAivenGroupsByTopic(admin)
            val cachedGroups = cache.getAivenGroupsByTopic(admin)

            // Assert
            assertSame(failure, exception)
            assertEquals(mapOf("events" to listOf("group-a")), groups)
            assertEquals(groups, cachedGroups)
            verify(exactly = 2) { admin.listGroups() }
        }

        @Test
        fun `Henter både grupper og offsets på nytt etter fem minutter`() {
            // Arrange
            val admin = mockk<Admin>()
            val result = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns result
            every { result.all() } returnsMany listOf(
                KafkaFuture.completedFuture(groupListings("group-a")),
                KafkaFuture.completedFuture(groupListings("group-a", "group-b")),
            )
            mockOffsets(
                admin,
                mapOf(
                    "group-a" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                    "group-b" to mapOf(TopicPartition("events", 1) to OffsetAndMetadata(8)),
                ),
            )

            // Act
            val groups = cache.getAivenGroupsByTopic(admin)
            ticker.set(Duration.ofMinutes(5).toNanos() - 1)
            val groupsBeforeExpiry = cache.getAivenGroupsByTopic(admin)
            ticker.incrementAndGet()
            val groupsAfterExpiry = cache.getAivenGroupsByTopic(admin)

            // Assert
            assertEquals(mapOf("events" to listOf("group-a")), groups)
            assertEquals(groups, groupsBeforeExpiry)
            assertEquals(mapOf("events" to listOf("group-a", "group-b")), groupsAfterExpiry)
            verify(exactly = 2) { admin.listGroups() }
            verify(exactly = 2) { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) }
        }

        @Test
        fun `Utelater grupper slettet under offset-oppslaget`() {
            val admin = mockk<Admin>()
            val result = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns result
            every { result.all() } returns KafkaFuture.completedFuture(groupListings("deleted", "group-a"))
            val offsetsResult = mockOffsets(
                admin,
                mapOf(
                    "deleted" to emptyMap(),
                    "group-a" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                ),
            )
            every { offsetsResult.partitionsToOffsetAndMetadata("deleted") } returns mockk {
                every { get() } throws ExecutionException(GroupIdNotFoundException("deleted"))
            }

            val groups = cache.getAivenGroupsByTopic(admin)
            val cachedGroups = cache.getAivenGroupsByTopic(admin)

            assertEquals(mapOf("events" to listOf("group-a")), groups)
            assertEquals(groups, cachedGroups)
            verify(exactly = 1) { admin.listGroups() }
        }

        @Test
        fun `Propagerer tilgangsfeil og tidsavbrudd uten å cache delvise resultater`() {
            for (cause in listOf(GroupAuthorizationException("synthetic group"), TimeoutException("synthetic timeout"))) {
                resetCache()
                val admin = mockk<Admin>()
                val result = mockk<ListGroupsResult>()
                every { admin.listGroups() } returns result
                every { result.all() } returns KafkaFuture.completedFuture(groupListings("group-a", "group-b"))
                val offsetsResult = mockOffsets(
                    admin,
                    mapOf(
                        "group-a" to mapOf(TopicPartition("events", 0) to OffsetAndMetadata(4)),
                        "group-b" to mapOf(TopicPartition("events", 1) to OffsetAndMetadata(8)),
                    ),
                )
                val failure = ExecutionException(cause)
                val failedFuture = mockk<KafkaFuture<Map<TopicPartition, OffsetAndMetadata>>>()
                every { failedFuture.get() } throws failure
                every { offsetsResult.partitionsToOffsetAndMetadata("group-b") } returnsMany listOf(
                    failedFuture,
                    KafkaFuture.completedFuture(mapOf(TopicPartition("events", 1) to OffsetAndMetadata(8))),
                )

                val exception = assertThrows(ExecutionException::class.java) {
                    cache.getAivenGroupsByTopic(admin)
                }
                val groups = cache.getAivenGroupsByTopic(admin)

                assertSame(failure, exception)
                assertEquals(mapOf("events" to listOf("group-a", "group-b")), groups)
                verify(exactly = 2) { admin.listGroups() }
            }
        }

        @Test
        fun `Deler ett samtidig cacheoppslag mellom administrasjonsklienter`() {
            val admin = mockk<Admin>()
            val anotherAdmin = mockk<Admin>()
            val result = mockk<ListGroupsResult>()
            every { admin.listGroups() } returns result
            val lookupStarted = CountDownLatch(1)
            val releaseLookup = CountDownLatch(1)
            every { result.all() } answers {
                lookupStarted.countDown()
                check(releaseLookup.await(10, TimeUnit.SECONDS))
                KafkaFuture.completedFuture(emptyList())
            }
            val secondRequestStarted = CountDownLatch(1)

            Executors.newFixedThreadPool(2).use { executor ->
                val first = executor.submit<Map<String, List<String>>> { cache.getAivenGroupsByTopic(admin) }
                try {
                    check(lookupStarted.await(10, TimeUnit.SECONDS))
                    val second = executor.submit<Map<String, List<String>>> {
                        secondRequestStarted.countDown()
                        cache.getAivenGroupsByTopic(anotherAdmin)
                    }
                    check(secondRequestStarted.await(10, TimeUnit.SECONDS))
                    releaseLookup.countDown()

                    assertEquals(emptyMap<String, List<String>>(), first.get(10, TimeUnit.SECONDS))
                    assertEquals(emptyMap<String, List<String>>(), second.get(10, TimeUnit.SECONDS))
                } finally {
                    releaseLookup.countDown()
                }
            }

            verify(exactly = 1) { admin.listGroups() }
            verify(exactly = 0) { anotherAdmin.listGroups() }
        }

        private fun mockOffsets(
            admin: Admin,
            offsetsByGroup: Map<String, Map<TopicPartition, OffsetAndMetadata>>,
        ): ListConsumerGroupOffsetsResult {
            val result = mockk<ListConsumerGroupOffsetsResult>()
            every { admin.listConsumerGroupOffsets(any<Map<String, ListConsumerGroupOffsetsSpec>>()) } returns result
            offsetsByGroup.forEach { (groupId, offsets) ->
                every { result.partitionsToOffsetAndMetadata(groupId) } returns KafkaFuture.completedFuture(offsets)
            }
            return result
        }

        private fun groupListings(vararg groupIds: String): List<GroupListing> = groupIds.map { id ->
            mockk<GroupListing> {
                every { groupId() } returns id
            }
        }

        @TestConfiguration(proxyBeanMethods = false)
        @EnableCaching
        @Import(KafkaConsumerGroupCache::class)
        class TestCachingConfiguration {
            @Bean
            fun ticker(): AtomicLong = AtomicLong()

            @Bean
            fun cacheManager(ticker: AtomicLong): CaffeineCacheManager = CaffeineCacheManager("consumer-groups").apply {
                setCaffeine(
                    Caffeine
                        .newBuilder()
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .ticker { ticker.get() },
                )
            }
        }
    }
