package no.nav.kafkamanager.controller

import no.nav.kafkamanager.domain.AvailableTopic
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.service.KafkaConsumerGroupService
import no.nav.kafkamanager.service.KafkaRecordReaderService
import no.nav.kafkamanager.service.KafkaTopicOperationsService
import no.nav.kafkamanager.utils.DtoMappers.toTopicWithOffset
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Eksponerer endepunkter for oppslag og operasjoner på Kafka-topics.
 *
 * @param kafkaTopicOperationsService utfører oppslag av Kafka-topics, end offsets og sletting av poster.
 * @param kafkaConsumerGroupService henter consumer-grupper og håndterer committed offsets.
 * @param kafkaRecordReaderService leser poster fra Kafka-topics.
 */
@RestController
@RequestMapping("/api/kafka")
class KafkaAdminController(
    private val kafkaTopicOperationsService: KafkaTopicOperationsService,
    private val kafkaConsumerGroupService: KafkaConsumerGroupService,
    private val kafkaRecordReaderService: KafkaRecordReaderService,
) {
    /**
     * Henter Kafka-topicene som er konfigurert for tjenesten.
     *
     * @return Kafka-topic-navn og informasjon om støtte for sletting av poster.
     */
    @GetMapping("/available-topics")
    fun availableTopics(): List<AvailableTopic> = kafkaTopicOperationsService.getAvailableTopics()

    /**
     * Leser poster fra et Kafka-topic etter at forespørselen er validert.
     *
     * @param request Kafka-topic, partisjonsvalg, startforskyvning, maksimal mengde og filter.
     * @return postene som samsvarer med forespørselen.
     * @throws ResponseStatusException hvis maksimal mengde er utenfor intervallet 0–100.
     */
    @PostMapping("/read-topic")
    fun readTopic(
        @RequestBody request: ReadTopicRequest,
    ): List<KafkaRecord> {
        validateReadTopicRequestDTO(request)
        return kafkaRecordReaderService.readTopic(request)
    }

    /**
     * Henter consumer-gruppens committed offsets og mapper dem til responsformatet.
     *
     * @param request Kafka-topic og consumer-gruppe.
     * @return committed offsets for Kafka-topicets partisjoner.
     */
    @PostMapping("/get-consumer-offsets")
    fun getOffsets(
        @RequestBody request: GetConsumerOffsetsRequest,
    ): List<TopicWithOffset> = kafkaConsumerGroupService.getConsumerOffsets(request).entries.map {
        toTopicWithOffset(
            topicPartition = it.key,
            offsetAndMetadata = it.value,
        )
    }

    /**
     * Henter endeforskyvningen for én partisjon.
     *
     * @param request Kafka-topic og partisjonsnummer.
     * @return siste tilgjengelige forskyvning.
     */
    @PostMapping("/get-last-record-offset")
    fun getLastRecordOffset(
        @RequestBody request: GetLastRecordOffsetRequest,
    ): GetLastRecordOffsetResponse = GetLastRecordOffsetResponse(kafkaTopicOperationsService.getLastRecordOffset(request))

    /**
     * Henter endeforskyvningen for alle partisjonene i et Kafka-topic.
     *
     * @param request Kafka-topic-navnet.
     * @return forskyvninger sortert etter partisjonsnummer.
     */
    @PostMapping("/get-last-record-offsets")
    fun getLastRecordOffsets(
        @RequestBody request: GetLastRecordOffsetsRequest,
    ): List<TopicWithOffset> = kafkaTopicOperationsService.getLastRecordOffsets(request)

    /**
     * Setter og lagrer offsetten for én partisjon i en consumer-gruppe.
     *
     * @param request Kafka-topic, gruppe, partisjon og ny offset.
     */
    @PostMapping("/set-consumer-offset")
    fun setConsumerOffset(
        @RequestBody request: SetConsumerOffsetRequest,
    ) {
        kafkaConsumerGroupService.setConsumerOffset(request)
    }

    /**
     * Henter consumer-gruppene som har committed offsets for Kafka-topicet.
     *
     * @param request Kafka-topic-navnet.
     * @return sorterte consumer-gruppe-ID-er.
     */
    @PostMapping("/get-consumer-groups")
    fun getConsumerGroups(
        @RequestBody request: GetConsumerGroupsRequest,
    ): List<String> = kafkaConsumerGroupService.getConsumerGroups(request)

    /**
     * Sletter poster fra en partisjon før den angitte forskyvningen.
     *
     * @param request Kafka-topic, partisjon og grenseforskyvning.
     */
    @PostMapping("/delete-records")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRecords(
        @RequestBody request: DeleteRecordsRequest,
    ) {
        kafkaTopicOperationsService.deleteRecords(
            topicName = request.topicName,
            partition = request.topicPartition,
            beforeOffset = request.beforeOffset,
        )
    }

    /**
     * Avviser forespørsler som ber om flere poster enn API-et tillater.
     *
     * @param readTopicRequest forespørselen som skal valideres.
     * @throws ResponseStatusException hvis maksimal mengde er utenfor intervallet 0–100.
     */
    private fun validateReadTopicRequestDTO(readTopicRequest: ReadTopicRequest) {
        if (readTopicRequest.maxRecords !in 0..MAX_KAFKA_RECORDS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRecords must be between 0 and 100")
        }
    }

    companion object {
        const val MAX_KAFKA_RECORDS = 100
    }

    /**
     * Inndata for lesing av poster fra et Kafka-topic.
     *
     * @property topicName navnet på Kafka-topicet.
     * @property topicAllPartitions om alle partisjoner skal leses.
     * @property topicPartition partisjonen som leses når `topicAllPartitions` er `false`.
     * @property maxRecords maksimalt antall poster i svaret.
     * @property fromOffset startforskyvningen.
     * @property filter valgfritt tekstfilter for nøkkel og verdi.
     */
    data class ReadTopicRequest(
        val topicName: String,
        val topicAllPartitions: Boolean,
        val topicPartition: Int,
        val maxRecords: Int,
        val fromOffset: Long,
        val filter: RecordFilter?,
    )

    /**
     * Tekst som brukes til å filtrere nøkkel og verdi i poster.
     *
     * @property text teksten som skal finnes i nøkkelen eller verdien.
     */
    data class RecordFilter(
        val text: String?,
    )

    /**
     * Inndata for å hente endeforskyvningen til én partisjon.
     *
     * @property topicName navnet på Kafka-topicet.
     * @property topicPartition partisjonsnummeret.
     */
    data class GetLastRecordOffsetRequest(
        val topicName: String,
        val topicPartition: Int,
    )

    /**
     * Inndata for å hente endeforskyvningene til alle partisjonene i et Kafka-topic.
     *
     * @property topicName navnet på Kafka-topicet.
     */
    data class GetLastRecordOffsetsRequest(
        val topicName: String,
    )

    /**
     * Svar med endeforskyvningen for én partisjon.
     *
     * @property latestRecordOffset siste tilgjengelige forskyvning.
     */
    data class GetLastRecordOffsetResponse(
        val latestRecordOffset: Long,
    )

    /**
     * Inndata for å hente consumer-gruppens committed offsets.
     *
     * @property groupId ID-en til consumer-gruppen.
     * @property topicName navnet på Kafka-topicet.
     */
    data class GetConsumerOffsetsRequest(
        val groupId: String,
        val topicName: String,
    )

    /**
     * Inndata for å endre offsetten til en consumer-gruppe.
     *
     * @property groupId ID-en til consumer-gruppen.
     * @property topicName navnet på Kafka-topicet.
     * @property topicPartition partisjonsnummeret.
     * @property offset den nye offsetten.
     */
    data class SetConsumerOffsetRequest(
        val groupId: String,
        val topicName: String,
        val topicPartition: Int,
        val offset: Long,
    )

    /**
     * Kafka-topic, partisjon og forskyvning i en offsetrespons.
     *
     * @property topicName navnet på Kafka-topicet.
     * @property topicPartition partisjonsnummeret.
     * @property offset forskyvningen.
     */
    data class TopicWithOffset(
        val topicName: String,
        val topicPartition: Int,
        val offset: Long,
    )

    /**
     * Inndata for å hente consumer-grupper som bruker et Kafka-topic.
     *
     * @property topicName navnet på Kafka-topicet.
     */
    data class GetConsumerGroupsRequest(
        val topicName: String,
    )

    /**
     * Inndata for å slette poster fra en partisjon.
     *
     * @property topicName navnet på Kafka-topicet.
     * @property topicPartition partisjonsnummeret.
     * @property beforeOffset slett poster før denne forskyvningen.
     */
    data class DeleteRecordsRequest(
        val topicName: String,
        val topicPartition: Int,
        val beforeOffset: Long,
    )
}
