package no.nav.kafkamanager.utils

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.common.kafka.util.KafkaEnvironmentVariables.*
import no.nav.common.kafka.util.KafkaPropertiesBuilder
import no.nav.common.utils.Credentials
import no.nav.common.utils.EnvironmentUtils.getRequiredProperty
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.DeserializerType
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.serialization.*
import java.util.*

object KafkaPropertiesFactory {

    fun createOnPremConsumerProperties(
        kafkaBrokerUrl: String,
        credentials: Credentials,
        schemaRegistryUrl: String?,
        keyDeserializerType: DeserializerType,
        valueDeserializerType: DeserializerType
    ): Properties {
        val builder = KafkaPropertiesBuilder.consumerBuilder()
            .withBaseProperties()
            .withProp(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, KafkaAdminController.MAX_KAFKA_RECORDS)
            .withBrokerUrl(kafkaBrokerUrl)
            .withOnPremAuth(credentials.username, credentials.password)
            .withProp(KEY_DESERIALIZER_CLASS_CONFIG, findDeserializer(keyDeserializerType).name)
            .withProp(VALUE_DESERIALIZER_CLASS_CONFIG, findDeserializer(valueDeserializerType).name)

        if (schemaRegistryUrl != null) {
            if (schemaRegistryUrl.isNotBlank()) {
                builder.withProp(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl)
            }
        }

        return builder.build()
    }

    fun createAivenConsumerProperties(
        keyDeserializerType: DeserializerType,
        valueDeserializerType: DeserializerType
    ): Properties {
        val schemaRegistryUrl = getRequiredProperty(KAFKA_SCHEMA_REGISTRY)
        val schemaRegistryUsername = getRequiredProperty(KAFKA_SCHEMA_REGISTRY_USER)
        val schemaRegistryPassword = getRequiredProperty(KAFKA_SCHEMA_REGISTRY_PASSWORD)

        return KafkaPropertiesBuilder.consumerBuilder()
            .withBaseProperties()
            .withProp(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, KafkaAdminController.MAX_KAFKA_RECORDS)
            .withAivenBrokerUrl()
            .withAivenAuth()
            .withProp(KEY_DESERIALIZER_CLASS_CONFIG, findDeserializer(keyDeserializerType).name)
            .withProp(VALUE_DESERIALIZER_CLASS_CONFIG, findDeserializer(valueDeserializerType).name)
            .withProp(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl)
            .withProp(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            .withProp(KafkaAvroSerializerConfig.USER_INFO_CONFIG, "$schemaRegistryUsername:$schemaRegistryPassword")
            .build()
    }

    private fun findDeserializer(deserializerType: DeserializerType): Class<*> {
        return when (deserializerType) {
            DeserializerType.STRING -> StringDeserializer::class.java
            DeserializerType.DOUBLE -> DoubleDeserializer::class.java
            DeserializerType.FLOAT -> FloatDeserializer::class.java
            DeserializerType.INTEGER -> IntegerDeserializer::class.java
            DeserializerType.LONG -> LongDeserializer::class.java
            DeserializerType.SHORT -> ShortDeserializer::class.java
            DeserializerType.UUID -> UUIDDeserializer::class.java
            DeserializerType.AVRO -> KafkaAvroDeserializer::class.java
        }
    }

}