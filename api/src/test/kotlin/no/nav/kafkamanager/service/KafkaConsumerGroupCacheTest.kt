package no.nav.kafkamanager.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.GroupListing
import org.apache.kafka.clients.admin.ListGroupsResult
import org.apache.kafka.common.KafkaFuture
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
import java.util.concurrent.atomic.AtomicLong

@SpringJUnitConfig(KafkaConsumerGroupCacheTest.TestCachingConfiguration::class)
class KafkaConsumerGroupCacheTest @Autowired constructor(
    private val cache: KafkaConsumerGroupCache,
    private val cacheManager: CaffeineCacheManager,
    private val ticker: AtomicLong,
) {
    @BeforeEach
    fun resetCache() {
        requireNotNull(cacheManager.getCache("consumer-group-ids")).clear()
        ticker.set(0)
    }

    @Test
    fun `Cacher gruppe-ID-er på tvers av administrasjonsklienter`() {
        // Arrange
        val admin = mockk<Admin>()
        val anotherAdmin = mockk<Admin>()
        val result = mockk<ListGroupsResult>()
        every { admin.listGroups() } returns result
        every { result.all() } returns KafkaFuture.completedFuture(groupListings("group-a", "group-b"))

        // Act
        val groups = cache.getAivenGroupIds(admin)
        val cachedGroups = cache.getAivenGroupIds(admin)
        val groupsFromAnotherAdmin = cache.getAivenGroupIds(anotherAdmin)

        // Assert
        assertEquals(listOf("group-a", "group-b"), groups)
        assertEquals(groups, cachedGroups)
        assertEquals(groups, groupsFromAnotherAdmin)
        verify(exactly = 1) { admin.listGroups() }
        verify(exactly = 0) { anotherAdmin.listGroups() }
    }

    @Test
    fun `Cacher en tom gruppeliste`() {
        // Arrange
        val admin = mockk<Admin>()
        val result = mockk<ListGroupsResult>()
        every { admin.listGroups() } returns result
        every { result.all() } returns KafkaFuture.completedFuture(emptyList())

        // Act
        val groups = cache.getAivenGroupIds(admin)
        val cachedGroups = cache.getAivenGroupIds(admin)

        // Assert
        assertEquals(emptyList<String>(), groups)
        assertEquals(emptyList<String>(), cachedGroups)
        verify(exactly = 1) { admin.listGroups() }
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

        // Act
        val exception = assertThrows(IllegalStateException::class.java) {
            cache.getAivenGroupIds(admin)
        }
        val groups = cache.getAivenGroupIds(admin)
        val cachedGroups = cache.getAivenGroupIds(admin)

        // Assert
        assertSame(failure, exception)
        assertEquals(listOf("group-a"), groups)
        assertEquals(groups, cachedGroups)
        verify(exactly = 2) { admin.listGroups() }
    }

    @Test
    fun `Henter gruppe-ID-er på nytt når TTL utløper`() {
        // Arrange
        val admin = mockk<Admin>()
        val result = mockk<ListGroupsResult>()
        every { admin.listGroups() } returns result
        every { result.all() } returnsMany listOf(
            KafkaFuture.completedFuture(groupListings("group-a")),
            KafkaFuture.completedFuture(groupListings("group-a", "group-b")),
        )

        // Act
        val groups = cache.getAivenGroupIds(admin)
        ticker.set(Duration.ofHours(1).toNanos() - 1)
        val groupsBeforeExpiry = cache.getAivenGroupIds(admin)
        ticker.incrementAndGet()
        val groupsAfterExpiry = cache.getAivenGroupIds(admin)

        // Assert
        assertEquals(listOf("group-a"), groups)
        assertEquals(groups, groupsBeforeExpiry)
        assertEquals(listOf("group-a", "group-b"), groupsAfterExpiry)
        verify(exactly = 2) { admin.listGroups() }
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
        fun cacheManager(ticker: AtomicLong): CaffeineCacheManager =
            CaffeineCacheManager("consumer-group-ids").apply {
                setCaffeine(
                    Caffeine
                        .newBuilder()
                        .expireAfterWrite(Duration.ofHours(1))
                        .ticker { ticker.get() },
                )
            }
    }
}
