package no.nav.kafkamanager.service

import no.nav.kafkamanager.controller.KafkaAdminController
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.springframework.stereotype.Service

/**
 * Leser consumer-grupper og håndterer consumer-gruppenes committed offsets for Kafka-topics.
 *
 * @param kafkaClientFactory oppretter Kafka-klienter for Kafka-topicene.
 */
@Service
class KafkaConsumerGroupService(
    private val kafkaClientFactory: KafkaClientFactory,
) {
    /**
     * Henter sorterte ID-er for consumer-grupper som har committed offsets for Kafka-topicet.
     *
     * Grupper det ikke er mulig å hente committed offsets for, utelates fra resultatet.
     *
     * @param request inneholder navnet på Kafka-topicet som skal undersøkes.
     * @return sorterte ID-er for consumer-gruppene som har committed offsets for Kafka-topicet.
     */
    fun getConsumerGroups(request: KafkaAdminController.GetConsumerGroupsRequest): List<String> {
        val adminClient = kafkaClientFactory.createKafkaAdminClientForTopic(request.topicName)

        adminClient.use { admin ->
            val consumerGroups = admin.listGroups().all().get()
            val consumerGroupIds = consumerGroups.map { it.groupId() }

            val groupsForTopic = consumerGroupIds.filter { groupId ->
                try {
                    val topicPartitions = admin
                        .listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata()
                        .get()
                        .keys
                        .map { it.topic() }
                        .toSet()

                    topicPartitions.contains(request.topicName)
                } catch (_: Exception) {
                    false
                }
            }

            return groupsForTopic.sorted()
        }
    }

    /**
     * Henter consumer-gruppens committed offsets for alle partisjonene i Kafka-topicet.
     *
     * @param request inneholder Kafka-topic-navnet og ID-en til consumer-gruppen.
     * @return committed offsets indeksert etter partisjon; partisjoner uten en offset utelates.
     */
    fun getConsumerOffsets(request: KafkaAdminController.GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata> {
        val kafkaConsumer = kafkaClientFactory.createKafkaConsumerForTopic(
            consumerGroupId = request.groupId,
            topicName = request.topicName,
        )

        kafkaConsumer.use { consumer ->
            val topicPartitions = consumer
                .partitionsFor(request.topicName)
                .map { TopicPartition(it.topic(), it.partition()) }
                .toSet()

            val committed = consumer.committed(topicPartitions)

            committed.values.removeIf { it == null }

            return committed
        }
    }

    /**
     * Setter og committer offsetten for én partisjon i den angitte consumer-gruppen.
     *
     * @param request inneholder Kafka-topic-navn, partisjonsnummer, gruppe-ID og offset.
     */
    fun setConsumerOffset(request: KafkaAdminController.SetConsumerOffsetRequest) {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = kafkaClientFactory.createKafkaConsumerForTopic(
            consumerGroupId = request.groupId,
            topicName = request.topicName,
        )

        kafkaConsumer.use { consumer ->
            consumer.assign(listOf(topicPartition))
            consumer.seek(topicPartition, request.offset)
            consumer.commitSync()
        }
    }
}
