package no.nav.kafkamanager.service

import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.utils.ConsumerRecordMapper
import no.nav.kafkamanager.utils.DtoMappers.toKafkaRecordHeader
import org.apache.kafka.common.TopicPartition
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Locale
import kotlin.math.max

/**
 * Leser poster fra Kafka-topics og filtrerer resultatet ved behov.
 *
 * @param kafkaClientFactory oppretter Kafka-consumer-klienter for Kafka-topicene.
 */
@Service
class KafkaRecordReaderService(
    private val kafkaClientFactory: KafkaClientFactory,
) {
    /**
     * Leser poster fra angitte partisjoner fra en forskyvning, fram til lesingen ikke gir flere poster
     * eller det ønskede maksimale antallet er nådd.
     *
     * Negativ startforskyvning behandles som null. Et eventuelt tekstfilter brukes på nøkkel og verdi.
     *
     * @param request inneholder Kafka-topic, partisjonsvalg, startforskyvning, maksimal mengde og filter.
     * @return de leste postene, begrenset til det maksimale antallet som er angitt i forespørselen.
     */
    fun readTopic(request: KafkaAdminController.ReadTopicRequest): List<KafkaRecord> {
        val kafkaConsumer = kafkaClientFactory.createKafkaConsumerForTopic(
            consumerGroupId = null,
            topicName = request.topicName,
        )
        val kafkaRecords = ArrayList<KafkaRecord>()

        kafkaConsumer.use { consumer ->
            val topicPartitions: List<TopicPartition> = if (request.topicAllPartitions) {
                consumer.partitionsFor(request.topicName).map {
                    TopicPartition(it.topic(), it.partition())
                }
            } else {
                listOf(TopicPartition(request.topicName, request.topicPartition))
            }

            val fromOffset = max(0, request.fromOffset)
            val maxRecords = request.maxRecords

            consumer.assign(topicPartitions)
            topicPartitions.forEach { consumer.seek(it, fromOffset) }

            while (kafkaRecords.size < maxRecords) {
                val consumerRecords = consumer.poll(Duration.ofSeconds(1))

                // No more records to consume right now
                if (consumerRecords.isEmpty) {
                    break
                }

                val kafkaRecordsBatch = consumerRecords.records(request.topicName).map {
                    val stringRecord = ConsumerRecordMapper.mapConsumerRecord(it)
                    toKafkaRecordHeader(stringRecord)
                }

                val filteredRecords = filterRecords(
                    filter = request.filter,
                    records = kafkaRecordsBatch,
                )

                kafkaRecords.addAll(filteredRecords)
            }

            return kafkaRecords.take(request.maxRecords)
        }
    }

    companion object {
        /**
         * Filtrerer poster etter tekst i nøkkel eller verdi.
         *
         * Sammenligningen ignorerer forskjeller i store og små bokstaver etter gjeldende standardlokale,
         * samt vanlige mellomrom og LF-linjeskift. Et manglende eller blankt filter gir alle postene tilbake.
         *
         * @param filter filteret som skal brukes, eller `null` for å ikke filtrere.
         * @param records postene som skal filtreres.
         * @return postene som samsvarer med filteret, i opprinnelig rekkefølge.
         */
        fun filterRecords(
            filter: KafkaAdminController.RecordFilter?,
            records: List<KafkaRecord>,
        ): List<KafkaRecord> {
            if (filter == null || filter.text.isNullOrBlank()) {
                return records
            }

            return records.filter {
                val filterText = insensitiveText(filter.text)
                val keyMatches = it.key != null && insensitiveText(it.key).contains(filterText)
                val valueMatches = it.value != null && insensitiveText(it.value).contains(filterText)

                return@filter keyMatches || valueMatches
            }
        }

        /**
         * Gjør tekst om til sammenligningsformat ved å bruke gjeldende standardlokale og fjerne
         * vanlige mellomrom og LF-linjeskift.
         *
         * @param str teksten som skal normaliseres.
         * @return teksten med små bokstaver uten vanlige mellomrom eller LF-linjeskift.
         */
        private fun insensitiveText(str: String): String = str
            .lowercase(Locale.getDefault())
            .replace(" ", "")
            .replace("\n", "")
    }
}
