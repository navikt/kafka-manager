package no.nav.kafkamanager.domain

data class KafkaRecord(
    val key: String?,
    val value: String?,
    val headers: List<KafkaRecordHeader>,
    val timestamp: Long,
    val offset: Long
)

data class KafkaRecordHeader(
    val name: String,
    val value: String
)