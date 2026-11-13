package no.nav.kafkamanager.utils

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConsumerRecordMapperTest {
    @Test
    fun `null key and value remain null when mapping consumer record`() {
        val consumerRecord = ConsumerRecord<Any?, Any?>("topic", 0, 0L, null, null)

        val mappedRecord = ConsumerRecordMapper.mapConsumerRecord(consumerRecord)
        val kafkaRecord = DtoMappers.toKafkaRecordHeader(mappedRecord)

        assertNull(kafkaRecord.key)
        assertNull(kafkaRecord.value)
    }
}
