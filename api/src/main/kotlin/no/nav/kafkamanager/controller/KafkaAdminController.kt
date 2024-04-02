package no.nav.kafkamanager.controller

import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.service.KafkaAdminService
import no.nav.kafkamanager.utils.DTOMappers.toTopicWithOffset
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/kafka")
class KafkaAdminController(
    val kafkaAdminService: KafkaAdminService,
) {

    @GetMapping("/available-topics")
    fun availableTopics(): List<String> {
        return kafkaAdminService.getAvailableTopics()
    }

    @PostMapping("/read-topic")
    fun readTopic(@RequestBody request: ReadTopicRequest): List<KafkaRecord> {
        validateReadTopicRequestDTO(request)
        return kafkaAdminService.readTopic(request)
    }

    @PostMapping("/get-consumer-offsets")
    fun getOffsets(@RequestBody request: GetConsumerOffsetsRequest): List<TopicWithOffset> {
        return kafkaAdminService.getConsumerOffsets(request).entries.map {
            toTopicWithOffset(it.key, it.value)
        }
    }

    @PostMapping("/get-last-record-offset")
    fun getLastRecordOffset(@RequestBody request: GetLastRecordOffsetRequest): GetLastRecordOffsetResponse {
        return GetLastRecordOffsetResponse(kafkaAdminService.getLastRecordOffset(request))
    }

    @PostMapping("/set-consumer-offset")
    fun setConsumerOffset(@RequestBody request: SetConsumerOffsetRequest) {
        kafkaAdminService.setConsumerOffset(request)
    }

    private fun validateReadTopicRequestDTO(readTopicRequest: ReadTopicRequest) {
        if (readTopicRequest.maxRecords < 0 || readTopicRequest.maxRecords > MAX_KAFKA_RECORDS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "maxRecords must be between 0 and 100")
        }
    }

    companion object {
        const val MAX_KAFKA_RECORDS = 100
    }

    data class ReadTopicRequest(
        val topicName: String,
        val topicAllPartitions: Boolean,
        val topicPartition: Int,
        val maxRecords: Int,
        val fromOffset: Long,
        val filter: RecordFilter?
    )

    data class RecordFilter(
        val text: String?
    )

    data class GetLastRecordOffsetRequest(
        val topicName: String,
        val topicPartition: Int
    )

    data class GetLastRecordOffsetResponse(
        val latestRecordOffset: Long
    )

    data class GetConsumerOffsetsRequest(
        val groupId: String,
        val topicName: String
    )

    data class SetConsumerOffsetRequest(
        val groupId: String,
        val topicName: String,
        val topicPartition: Int,
        val offset: Long
    )

    data class TopicWithOffset(
        val topicName: String,
        val topicPartition: Int,
        val offset: Long
    )

}
