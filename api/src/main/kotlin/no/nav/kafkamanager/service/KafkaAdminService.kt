package no.nav.kafkamanager.service

import no.nav.common.utils.Credentials
import no.nav.kafkamanager.config.EnvironmentProperties
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.domain.TopicConfig
import no.nav.kafkamanager.domain.TopicLocation
import no.nav.kafkamanager.utils.ConsumerRecordMapper
import no.nav.kafkamanager.utils.DTOMappers.toKafkaRecordHeader
import no.nav.kafkamanager.utils.KafkaPropertiesFactory.createAivenConsumerProperties
import no.nav.kafkamanager.utils.KafkaPropertiesFactory.createOnPremConsumerProperties
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.*
import java.util.Collections.singleton
import java.util.function.Supplier
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

@Service
class KafkaAdminService(
    private val environmentProperties: EnvironmentProperties,
    private val appConfig: AppConfig,
    private val systemUserCredentialsSupplier: Supplier<Credentials>
) {

    fun getAvailableTopics(): List<String> {
        return appConfig.topics.map { it.name }
    }

    fun readTopic(request: KafkaAdminController.ReadTopicRequest): List<KafkaRecord> {
        val kafkaConsumer = createKafkaConsumerForTopic(null, request.topicName)
        val kafkaRecords = ArrayList<KafkaRecord>()

        kafkaConsumer.use { consumer ->
            val topicPartition = TopicPartition(request.topicName, request.topicPartition)
            val fromOffset = max(0, request.fromOffset)
            val maxRecords = request.maxRecords

            consumer.assign(singleton(topicPartition))
            consumer.seek(topicPartition, fromOffset)

            while (kafkaRecords.size < maxRecords) {
                val consumerRecords = consumer.poll(Duration.ofSeconds(1))

                // No more records to consume right now
                if (consumerRecords.isEmpty) {
                    break
                }

                val kafkaRecordsBatch = consumerRecords.records(topicPartition).map {
                    val stringRecord = ConsumerRecordMapper.mapConsumerRecord(it)
                    toKafkaRecordHeader(stringRecord)
                }

                val filteredRecords = filterRecords(request.filter, kafkaRecordsBatch)

                kafkaRecords.addAll(filteredRecords)
            }

            // Shrink to fit maxRecords
            if (kafkaRecords.size > maxRecords) {
                return kafkaRecords.subList(0, min(kafkaRecords.size, maxRecords))
            }

            return kafkaRecords
        }
    }

    fun getLastRecordOffset(request: KafkaAdminController.GetLastRecordOffsetRequest): Long {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = createKafkaConsumerForTopic(null, request.topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(singleton(topicPartition))
            consumer.seekToEnd(singleton(topicPartition))

            return consumer.position(topicPartition)
        }
    }

    fun getConsumerOffsets(request: KafkaAdminController.GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata> {
        val kafkaConsumer = createKafkaConsumerForTopic(request.groupId, request.topicName)

        kafkaConsumer.use { consumer ->
            val topicPartitions = consumer.partitionsFor(request.topicName)
                .map { TopicPartition(it.topic(), it.partition()) }
                .toSet()

            val committed = consumer.committed(topicPartitions)

            committed.values.removeIf { it == null }

            return committed
        }
    }

    fun setConsumerOffset(request: KafkaAdminController.SetConsumerOffsetRequest) {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = createKafkaConsumerForTopic(request.groupId, request.topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(listOf(topicPartition))
            consumer.seek(topicPartition, request.offset)
            consumer.commitSync()
        }
    }

    private fun createKafkaConsumerForTopic(
        consumerGroupId: String?,
        topicName: String
    ): KafkaConsumer<Any?, Any?> {
        val topicConfig = findTopicConfigOrThrow(topicName)
        val properties = createPropertiesForTopic(consumerGroupId, topicConfig)

        return KafkaConsumer(properties)
    }

    private fun findTopicConfigOrThrow(topicName: String): TopicConfig {
        return appConfig.topics.find { it.name == topicName }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
    }

    private fun createPropertiesForTopic(consumerGroupId: String?, topicConfig: TopicConfig): Properties {
        val keyDesType = topicConfig.keyDeserializerType
        val valueDesType = topicConfig.valueDeserializerType

        val properties = when (topicConfig.location) {
            TopicLocation.ON_PREM -> createOnPremConsumerProperties(
                environmentProperties.onPremKafkaBrokersUrl,
                systemUserCredentialsSupplier.get(),
                environmentProperties.onPremSchemaRegistryUrl,
                keyDesType,
                valueDesType
            )
            TopicLocation.AIVEN -> createAivenConsumerProperties(keyDesType, valueDesType)
        }

        if (consumerGroupId != null) {
            properties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroupId
        }

        return properties
    }

    companion object {

        fun filterRecords(
            filter: KafkaAdminController.RecordFilter?,
            records: List<KafkaRecord>
        ): List<KafkaRecord>  {
            if (filter == null || filter.text.isNullOrBlank()) {
                return records
            }

            return records.filter {
                val filterText = insensitiveText(filter.text)
                val keyMatches = it.key != null && insensitiveText(it.key).contains(filterText)
                val valueMatches = it.value != null && insensitiveText(it.value).contains(filterText)

                return@filter keyMatches || valueMatches
            }
        }

        private fun insensitiveText(str: String): String {
            return str.lowercase(Locale.getDefault())
                .replace(" ","")
                .replace("\n", "")
        }

    }

}