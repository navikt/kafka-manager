package no.nav.kafkamanager.service

import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.utils.Credentials
import no.nav.kafkamanager.config.EnvironmentProperties
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.controller.KafkaAdminController.Companion.MAX_KAFKA_RECORDS
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.domain.TopicConfig
import no.nav.kafkamanager.domain.TopicLocation
import no.nav.kafkamanager.utils.KafkaRecordDeserializer.deserializeRecord
import no.nav.kafkamanager.utils.Mappers.toKafkaRecordHeader
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
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
        val topicConfig = findTopicConfigOrThrow(request.topicName)
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
                    val stringRecord = deserializeRecord(it, topicConfig.keyDeserializerType, topicConfig.valueDeserializerType)
                    toKafkaRecordHeader(stringRecord)
                }

                kafkaRecords.addAll(kafkaRecordsBatch)
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
    ): KafkaConsumer<ByteArray, ByteArray> {
        val topicConfig = findTopicConfigOrThrow(topicName)
        val properties = createPropertiesForTopic(consumerGroupId, topicConfig)

        return KafkaConsumer(properties)
    }

    private fun findTopicConfigOrThrow(topicName: String): TopicConfig {
        return appConfig.topics.find { it.name == topicName }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
    }

    private fun createPropertiesForTopic(consumerGroupId: String?, topicConfig: TopicConfig): Properties {
        val properties = when (topicConfig.location) {
            TopicLocation.ON_PREM -> createOnPremConsumerProperties(systemUserCredentialsSupplier)
            TopicLocation.AIVEN -> createAivenConsumerProperties()
        }

        if (consumerGroupId != null) {
            properties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroupId
        }

        return properties
    }

    private fun createOnPremConsumerProperties(credentialsSupplier: Supplier<Credentials>): Properties {
        val credentials = credentialsSupplier.get()

        return KafkaPropertiesBuilder.consumerBuilder()
            .withBaseProperties()
            .withProp(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_KAFKA_RECORDS)
            .withBrokerUrl(environmentProperties.onPremKafkaBrokersUrl)
            .withOnPremAuth(credentials.username, credentials.password)
            .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
            .build()
    }

    private fun createAivenConsumerProperties(): Properties {
        return KafkaPropertiesBuilder.consumerBuilder()
            .withBaseProperties()
            .withProp(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_KAFKA_RECORDS)
            .withAivenBrokerUrl()
            .withAivenAuth()
            .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
            .build()
    }

}