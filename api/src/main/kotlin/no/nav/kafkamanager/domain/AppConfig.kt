package no.nav.kafkamanager.domain

data class AppConfig(
    val topics: List<TopicConfig>,
)

data class AvailableTopic(
    val name: String,
    val supportsRecordDeletion: Boolean,
)

data class TopicConfig(
    val name: String,
    val location: TopicLocation,
    val keyDeserializerType: DeserializerType,
    val valueDeserializerType: DeserializerType,
    val isolationLevel: String?,
) {
    val supportsRecordDeletion: Boolean
        get() = location == TopicLocation.AIVEN
}

enum class TopicLocation {
    ON_PREM,
    AIVEN,
}

enum class DeserializerType {
    STRING,
    DOUBLE,
    FLOAT,
    INTEGER,
    LONG,
    SHORT,
    UUID,
    AVRO,
}
