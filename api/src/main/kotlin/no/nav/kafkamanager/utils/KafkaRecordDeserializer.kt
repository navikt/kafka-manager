package no.nav.kafkamanager.utils

import no.nav.kafkamanager.domain.DeserializerType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.*

object KafkaRecordDeserializer {

    fun deserializeRecord(
        consumerRecord: ConsumerRecord<ByteArray, ByteArray>,
        keyDeserializerType: DeserializerType,
        valueDeserializerType: DeserializerType
    ): ConsumerRecord<String?, String?> {
        val keyDeserializer = findStringDeserializer(keyDeserializerType)
        val valueDeserializer = findStringDeserializer(valueDeserializerType)

        return deserializeRecord(consumerRecord, keyDeserializer, valueDeserializer)
    }

    private fun deserializeRecord(
        consumerRecord: ConsumerRecord<ByteArray, ByteArray>,
        keyDeserializer: Deserializer<String?>,
        valueDeserializer: Deserializer<String?>
    ): ConsumerRecord<String?, String?> {
        val topic = consumerRecord.topic()

        return ConsumerRecord(
            topic,
            consumerRecord.partition(),
            consumerRecord.offset(),
            consumerRecord.timestamp(),
            consumerRecord.timestampType(),
            -1,
            consumerRecord.serializedKeySize(),
            consumerRecord.serializedValueSize(),
            keyDeserializer.deserialize(topic, consumerRecord.key()),
            valueDeserializer.deserialize(topic, consumerRecord.value()),
            consumerRecord.headers()
        )
    }

    private fun findStringDeserializer(deserializerType: DeserializerType): Deserializer<String?> {
        return when (deserializerType) {
            DeserializerType.STRING -> StringDeserializer()
            DeserializerType.DOUBLE -> DoubleToStringDeserializer()
            DeserializerType.FLOAT -> FloatToStringDeserializer()
            DeserializerType.INTEGER -> IntegerToStringDeserializer()
            DeserializerType.LONG -> LongToStringDeserializer()
            DeserializerType.SHORT -> ShortToStringDeserializer()
            DeserializerType.UUID -> UUIDToStringDeserializer()
            DeserializerType.AVRO -> AvroToStringDeserializer()
        }
    }

    private class DoubleToStringDeserializer : Deserializer<String?> {
        private val doubleDeserializer = DoubleDeserializer()

        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return doubleDeserializer.deserialize(topic, data)?.toString()
        }
    }

    private class FloatToStringDeserializer : Deserializer<String?> {
        private val floatDeserializer = FloatDeserializer()

        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return floatDeserializer.deserialize(topic, data)?.toString()
        }
    }

    private class IntegerToStringDeserializer : Deserializer<String?> {
        private val integerDeserializer = IntegerDeserializer()

        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return integerDeserializer.deserialize(topic, data)?.toString()
        }
    }

    private class LongToStringDeserializer : Deserializer<String?> {
        private val longDeserializer = LongDeserializer()

        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return longDeserializer.deserialize(topic, data)?.toString()
        }
    }

    private class ShortToStringDeserializer : Deserializer<String?> {
        private val shortDeserializer = ShortDeserializer()

        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return shortDeserializer.deserialize(topic, data)?.toString()
        }
    }

    private class UUIDToStringDeserializer : Deserializer<String?> {
        private val uuidDeserializer = UUIDDeserializer()

        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return uuidDeserializer.deserialize(topic, data)?.toString()
        }
    }

    private class AvroToStringDeserializer : Deserializer<String?> {
        override fun deserialize(topic: String?, data: ByteArray?): String? {
            return TODO()
        }
    }

}