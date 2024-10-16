package no.nav.kafkamanager.domain

data class AppConfig (
    val topics: List<TopicConfig>
)

data class TopicConfig(
    val name: String,
    val location: TopicLocation,
    val keyDeserializerType: DeserializerType,
    val valueDeserializerType: DeserializerType,
    val isolationLevel: String?
)

enum class TopicLocation {
    ON_PREM,
    AIVEN
}

enum class DeserializerType {
    STRING,
    DOUBLE,
    FLOAT,
    INTEGER,
    LONG,
    SHORT,
    UUID,
    AVRO
}

