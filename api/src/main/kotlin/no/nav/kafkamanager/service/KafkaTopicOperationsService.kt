package no.nav.kafkamanager.service

import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.AvailableTopic
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.common.TopicPartition
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.Collections.singleton

/**
 * Utfører oppslag av topic-offsetter og sletting av poster.
 *
 * @param kafkaClientFactory oppretter Kafka-klienter for Kafka-topicene.
 * @param appConfig inneholder konfigurasjonen av tilgjengelige Kafka-topics.
 */
@Service
class KafkaTopicOperationsService(
    private val kafkaClientFactory: KafkaClientFactory,
    private val appConfig: AppConfig,
) {
    /**
     * Henter Kafka-topicene som er konfigurert i applikasjonen.
     *
     * @return tilgjengelige Kafka-topics med informasjon om støtte for sletting av poster.
     */
    fun getAvailableTopics(): List<AvailableTopic> = appConfig.topics.map { topic ->
        AvailableTopic(
            name = topic.name,
            supportsRecordDeletion = topic.supportsRecordDeletion,
        )
    }

    /**
     * Henter endeforskyvningen for én partisjon i et Kafka-topic.
     *
     * @param request inneholder Kafka-topic-navn og partisjonsnummer.
     * @return consumerens posisjon etter at den er flyttet til slutten av partisjonen.
     */
    fun getLastRecordOffset(request: KafkaAdminController.GetLastRecordOffsetRequest): Long {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = kafkaClientFactory.createKafkaConsumerForTopic(null, request.topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(singleton(topicPartition))
            consumer.seekToEnd(singleton(topicPartition))

            return consumer.position(topicPartition)
        }
    }

    /**
     * Henter endeforskyvningene for alle partisjonene i et Kafka-topic.
     *
     * @param request inneholder navnet på Kafka-topicet.
     * @return partisjonenes endeforskyvninger, sortert etter partisjonsnummer, eller en tom liste hvis Kafka-topicet
     * ikke har noen partisjoner.
     */
    fun getLastRecordOffsets(request: KafkaAdminController.GetLastRecordOffsetsRequest): List<KafkaAdminController.TopicWithOffset> {
        val kafkaConsumer = kafkaClientFactory.createKafkaConsumerForTopic(null, request.topicName)

        kafkaConsumer.use { consumer ->
            val topicPartitions = consumer.partitionsFor(request.topicName).map {
                TopicPartition(it.topic(), it.partition())
            }

            if (topicPartitions.isEmpty()) {
                return emptyList()
            }

            return consumer
                .endOffsets(topicPartitions)
                .map { (topicPartition, offset) ->
                    KafkaAdminController.TopicWithOffset(
                        topicName = topicPartition.topic(),
                        topicPartition = topicPartition.partition(),
                        offset = offset,
                    )
                }.sortedBy { it.topicPartition }
        }
    }

    /**
     * Sletter poster fra en partisjon i Kafka-topicet før den angitte forskyvningen.
     * Operasjonen venter til slettingen er fullført.
     *
     * @param topicName navnet på Kafka-topicet.
     * @param partition partisjonsnummeret.
     * @param beforeOffset slett alle poster før denne forskyvningen.
     * @throws ResponseStatusException hvis Kafka-topicet ikke støtter sletting, eller hvis slettingen mislykkes.
     */
    fun deleteRecords(
        topicName: String,
        partition: Int,
        beforeOffset: Long,
    ) {
        val adminClient = kafkaClientFactory.createKafkaAdminClientForTopic(topicName)
        val topicPartition = TopicPartition(topicName, partition)
        val recordsToDelete = mapOf(topicPartition to RecordsToDelete.beforeOffset(beforeOffset))
        adminClient.use { admin ->
            try {
                val result = admin.deleteRecords(recordsToDelete)
                result.all().get() // Wait for completion
            } catch (e: Exception) {
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete records: ${e.message}",
                    e,
                )
            }
        }
    }
}
