package no.nav.kafkamanager.service

import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.kafkamanager.config.EnvironmentProperties
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.utils.Mappers.toKafkaRecordHeader
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.Collections.singleton
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min


@Service
class KafkaAdminService(
    private val environmentProperties: EnvironmentProperties
) {

    fun readTopic(request: KafkaAdminController.ReadTopicRequest): List<KafkaRecord> {
        val topicName = request.topicName
        val partition = request.topicPartition
        val fromOffset = max(0, request.fromOffset)
        val maxRecords = request.maxRecords

        val properties = createConsumerProperties(null, request.credentials)
        properties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = maxRecords

        val kafkaConsumer = KafkaConsumer<ByteArray, ByteArray>(properties)

        val kafkaRecords = ArrayList<KafkaRecord>()

        kafkaConsumer.use { consumer ->
            val topicPartition = TopicPartition(topicName, partition)

            consumer.assign(singleton(topicPartition))
            consumer.seek(topicPartition, fromOffset)

            while (kafkaRecords.size < maxRecords) {
                val consumerRecords = consumer.poll(Duration.ofSeconds(1))

                // No more records to consume right now
                if (consumerRecords.isEmpty) {
                    break
                }

                kafkaRecords.addAll(consumerRecords.records(topicPartition).map { toKafkaRecordHeader(it) })
            }

            // Shrink to fit maxRecords
            if (kafkaRecords.size > maxRecords) {
                return kafkaRecords.subList(0, min(kafkaRecords.size, maxRecords))
            }

            return kafkaRecords
        }
    }

    fun getLastRecordOffset(offsetRequest: KafkaAdminController.GetLastRecordOffsetRequest): Long {
        val topicName = offsetRequest.topicName
        val partition = offsetRequest.topicPartition
        val topicPartition = TopicPartition(topicName, partition)

        val kafkaConsumer = createConsumer(null, offsetRequest.credentials)

        kafkaConsumer.use { consumer ->
            consumer.assign(singleton(topicPartition))
            consumer.seekToEnd(singleton(topicPartition))

            return consumer.position(topicPartition)
        }
    }

    fun getConsumerOffsets(request: KafkaAdminController.GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata> {
        val topicName = request.topicName
        val kafkaConsumer = createConsumer(request.groupId, request.credentials)

        kafkaConsumer.use { consumer ->
            val topicPartitions = consumer.partitionsFor(topicName)
                .map { TopicPartition(it.topic(), it.partition()) }
                .toSet()

            val committed = consumer.committed(topicPartitions)

            committed.values.removeIf { it == null }

            return committed
        }
    }

    fun setConsumerOffset(request: KafkaAdminController.SetConsumerOffsetRequest) {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = createConsumer(request.groupId, request.credentials)

        kafkaConsumer.use { consumer ->
            consumer.assign(listOf(topicPartition))
            consumer.seek(topicPartition, request.offset)
            consumer.commitSync()
        }
    }

    private fun createConsumerProperties(consumerGroupId: String?, credentials: KafkaAdminController.Credentials): Properties {
        val builder = KafkaPropertiesBuilder.consumerBuilder()
            .withBaseProperties()

        if (consumerGroupId != null) {
            builder.withConsumerGroupId(consumerGroupId)
        }

        return builder
            .withBrokerUrl(environmentProperties.onPremKafkaBrokersUrl)
            .withOnPremAuth(credentials.username, credentials.password)
            .withDeserializers(ByteArrayDeserializer::class.java, ByteArrayDeserializer::class.java)
            .build()
    }

    private fun createConsumer(consumerGroupId: String?, credentials: KafkaAdminController.Credentials): KafkaConsumer<ByteArray, ByteArray> {
        return KafkaConsumer(createConsumerProperties(consumerGroupId, credentials))
    }

}