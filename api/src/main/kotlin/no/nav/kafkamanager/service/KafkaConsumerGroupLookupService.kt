package no.nav.kafkamanager.service

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec
import org.apache.kafka.common.errors.GroupIdNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutionException
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

@Service
class KafkaConsumerGroupLookupService(
    private val kafkaClientFactory: KafkaClientFactory,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Cacheable(cacheNames = ["consumer-groups"], key = "'aiven'", sync = true)
    fun getAivenGroupsByTopic(): Map<String, List<String>> {
        val (groupsByTopic, duration) = measureTimedValue {
            kafkaClientFactory.createAivenAdminClient().use { admin ->
                loadGroupsByTopic(admin)
            }
        }
        logger.info(
            "Refreshed consumer group cache: topics={}, durationMs={}",
            groupsByTopic.size,
            duration.inWholeMilliseconds,
        )
        return groupsByTopic
    }

    private fun loadGroupsByTopic(admin: Admin): Map<String, List<String>> {
        val (groupIds, listingDuration) = measureTimedValue {
            admin
                .listGroups()
                .all()
                .get()
                .map { it.groupId() }
        }
        logger.info(
            "Listed consumer groups: groups={}, durationMs={}",
            groupIds.size,
            listingDuration.inWholeMilliseconds,
        )
        if (groupIds.isEmpty()) return emptyMap()

        val offsetsStarted = TimeSource.Monotonic.markNow()
        val offsetsResult = admin.listConsumerGroupOffsets(
            groupIds.associateWith { ListConsumerGroupOffsetsSpec() },
        )
        var groupsNotFound = 0

        val groupsByTopic = buildMap<String, MutableSet<String>> {
            for (groupId in groupIds) {
                val offsets = try {
                    offsetsResult.partitionsToOffsetAndMetadata(groupId).get()
                } catch (exception: ExecutionException) {
                    if (exception.cause !is GroupIdNotFoundException) throw exception
                    groupsNotFound++
                    continue
                }
                // Fyller map for buildMap med én oppføring per topic.
                // Henter eller oppretter settet og legger til gruppen kun én gang per topic,
                // uansett antall partisjoner.
                offsets.keys.forEach { partition ->
                    getOrPut(partition.topic()) {
                        mutableSetOf()
                    }.add(groupId)
                }
            }
        }
        logger.info(
            "Fetched consumer group offsets: groups={}, groupsNotFound={}, durationMs={}",
            groupIds.size,
            groupsNotFound,
            offsetsStarted.elapsedNow().inWholeMilliseconds,
        )

        // Beholder topic-navnene som nøkler og gjør hvert sett med gruppe-ID-er
        // om til en alfabetisk sortert liste.
        return groupsByTopic.mapValues { (_, groups) -> groups.sorted() }
    }
}
