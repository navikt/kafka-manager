package no.nav.kafkamanager.service

import org.apache.kafka.clients.admin.Admin
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class KafkaConsumerGroupCache {
    @Cacheable(cacheNames = ["consumer-group-ids"], key = "'aiven'", sync = true)
    fun getAivenGroupIds(admin: Admin): List<String> =
        admin.listGroups().all().get().map { it.groupId() }
}
