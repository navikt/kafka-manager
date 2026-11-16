package no.nav.kafkamanager.service

import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec
import org.apache.kafka.common.errors.GroupIdNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutionException

@Service
class KafkaConsumerGroupCache {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Cacheable(cacheNames = ["consumer-groups"], key = "'aiven'", sync = true)
    fun getAivenGroupsByTopic(admin: Admin): Map<String, List<String>> {
        val groupIds = admin
            .listGroups()
            .all()
            .get()
            .map { it.groupId() }
        if (groupIds.isEmpty()) return emptyMap()

        val offsetsResult = admin.listConsumerGroupOffsets(
            groupIds.associateWith { ListConsumerGroupOffsetsSpec() },
        )
        val groupsByTopic = mutableMapOf<String, MutableSet<String>>()

        for (groupId in groupIds) {
            val offsets = try {
                offsetsResult.partitionsToOffsetAndMetadata(groupId).get()
            } catch (exception: ExecutionException) {
                if (exception.cause !is GroupIdNotFoundException) throw exception
                logger.info("Skipping a consumer group deleted during the offset lookup")
                continue
            }
            offsets.keys.forEach { partition ->
                groupsByTopic.getOrPut(partition.topic()) { mutableSetOf() }.add(groupId)
            }
        }

        return groupsByTopic.mapValues { (_, groups) -> groups.sorted() }
    }
}
