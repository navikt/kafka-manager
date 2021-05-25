package no.nav.kafkamanager.utils

import no.nav.common.json.JsonUtils
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.*

object ConsumerRecordMapper {

    fun mapConsumerRecord(consumerRecord: ConsumerRecord<Any?, Any?>): ConsumerRecord<String, String> {
        return ConsumerRecord(
            consumerRecord.topic(),
            consumerRecord.partition(),
            consumerRecord.offset(),
            consumerRecord.timestamp(),
            consumerRecord.timestampType(),
            -1,
            consumerRecord.serializedKeySize(),
            consumerRecord.serializedValueSize(),
            convertToString(consumerRecord.key()),
            convertToString(consumerRecord.value()),
            consumerRecord.headers()
        )
    }

    private fun convertToString(keyOrValue: Any?): String {
        return when (keyOrValue) {
            is String -> keyOrValue
            is Double -> keyOrValue.toString()
            is Float -> keyOrValue.toString()
            is Int -> keyOrValue.toString()
            is Long -> keyOrValue.toString()
            is Short -> keyOrValue.toString()
            is UUID -> keyOrValue.toString()
            is GenericRecord -> JsonUtils.toJson(getRecordKeyValues(keyOrValue))
            else -> keyOrValue.toString()
        }
    }

    private fun getRecordKeyValues(genericRecord: GenericRecord): Map<String, String> {
        return genericRecord.schema.fields.fold(emptyMap()) { m, field ->
            val v = genericRecord[field.name()]?.toString() ?: ""
            m + (field.name() to v)
        }
    }

}