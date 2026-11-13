package no.nav.kafkamanager.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.service.KafkaRecordReaderService.Companion.filterRecords
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

/** Tester lesing, avgrensing og filtrering av poster fra Kafka. */
class KafkaRecordReaderServiceTest {
    /** Tester leseløpet og livssyklusen til consumeren. */
    @Nested
    inner class TopicReading {
    @Test
    fun `Leser valgt partisjon fra ikke-negativ offset og begrenser antall poster`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "events") } returns consumer
            val partition = TopicPartition("events", 2)
            val firstRecord = org.apache.kafka.clients.consumer.ConsumerRecord<Any?, Any?>(
                "events",
                2,
                0L,
                "first",
                "value-a",
            )
            val secondRecord = org.apache.kafka.clients.consumer.ConsumerRecord<Any?, Any?>(
                "events",
                2,
                1L,
                "second",
                "value-b",
            )
            val batch: ConsumerRecords<Any?, Any?> = ConsumerRecords(
                mapOf(partition to listOf(firstRecord, secondRecord)),
                emptyMap(),
            )
            every { consumer.poll(any<Duration>()) } returns batch
            val service = KafkaRecordReaderService(factory)

            // Act
            val records = service.readTopic(
                KafkaAdminController.ReadTopicRequest(
                    topicName = "events",
                    topicAllPartitions = false,
                    topicPartition = 2,
                    maxRecords = 1,
                    fromOffset = -5,
                    filter = null,
                ),
            )

            // Assert
            assertEquals(1, records.size)
            assertEquals(KafkaRecord(2, "first", "value-a", emptyList(), -1, 0), records.single())
            verify { consumer.assign(listOf(partition)) }
            verify { consumer.seek(partition, 0) }
            verify { consumer.close() }
        }

        @Test
        fun `Leser alle partisjoner filtrerer poster og avslutter når polling ikke gir poster`() {
            // Arrange
            val consumer = mockk<KafkaConsumer<Any?, Any?>>(relaxed = true)
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "events") } returns consumer
            every { consumer.partitionsFor("events") } returns listOf(
                PartitionInfo("events", 0, null, emptyArray(), emptyArray()),
                PartitionInfo("events", 1, null, emptyArray(), emptyArray()),
            )
            val first = TopicPartition("events", 0)
            val second = TopicPartition("events", 1)
            val matchRecord = org.apache.kafka.clients.consumer.ConsumerRecord<Any?, Any?>(
                "events",
                0,
                4L,
                "match",
                "yes",
            )
            val skipRecord = org.apache.kafka.clients.consumer.ConsumerRecord<Any?, Any?>(
                "events",
                1,
                5L,
                "skip",
                "no",
            )
            val batch: ConsumerRecords<Any?, Any?> = ConsumerRecords(
                mapOf(
                    first to listOf(matchRecord),
                    second to listOf(skipRecord),
                ),
                emptyMap(),
            )
            val emptyBatch: ConsumerRecords<Any?, Any?> = ConsumerRecords(emptyMap(), emptyMap())
            every { consumer.poll(any<Duration>()) } returnsMany listOf(batch, emptyBatch)
            val service = KafkaRecordReaderService(factory)

            // Act
            val records = service.readTopic(
                KafkaAdminController.ReadTopicRequest(
                    topicName = "events",
                    topicAllPartitions = true,
                    topicPartition = 0,
                    maxRecords = 10,
                    fromOffset = 4,
                    filter = KafkaAdminController.RecordFilter("MATCH"),
                ),
            )

            // Assert
            assertEquals(listOf("match"), records.map { it.key })
            assertEquals(listOf(0), records.map { it.partition })
            verify { consumer.assign(listOf(first, second)) }
            verify { consumer.seek(first, 4) }
            verify { consumer.seek(second, 4) }
            verify(exactly = 2) { consumer.poll(any<Duration>()) }
            verify { consumer.close() }
        }

        @Test
        fun `Avviser ukjente Kafka-topics før consumeren brukes`() {
            // Arrange
            val factory = mockk<KafkaClientFactory>()
            every { factory.createKafkaConsumerForTopic(null, "unknown-topic") } throws
                ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find config for topic")
            val service = KafkaRecordReaderService(factory)

            // Act
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.readTopic(
                    KafkaAdminController.ReadTopicRequest(
                        "unknown-topic",
                        false,
                        0,
                        10,
                        0,
                        null,
                    ),
                )
            }

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }

        @Test
        fun `Et null-filter lar alle poster passere uendret`() {
            // Arrange
            val records = sampleRecords

            // Act
            val filtered = filterRecords(null, records)

            // Assert
            assertEquals(records, filtered)
        }

        @Test
        fun `Blank filtertekst lar alle poster passere uendret`() {
            // Arrange
            val records = sampleRecords

            // Act
            val filtered = filterRecords(KafkaAdminController.RecordFilter("  "), records)

            // Assert
            assertEquals(records, filtered)
        }

        @Test
        fun `Null filtertekst lar alle poster passere uendret`() {
            // Arrange
            val records = sampleRecords

            // Act
            val filtered = filterRecords(KafkaAdminController.RecordFilter(null), records)

            // Assert
            assertEquals(records, filtered)
        }
    }

    /** Tester tekstmatching på nøkkel og verdi. */
    @Nested
    inner class RecordFiltering {
    @Test
    fun `Matcher filtertekst i nøkkel uten å skille mellom store bokstaver og mellomrom`() {
            // Arrange
            val records = sampleRecords

            // Act
            val filtered = filterRecords(KafkaAdminController.RecordFilter("KeY 123"), records)

            // Assert
            assertEquals(listOf("key123", "key1234"), filtered.map { it.key })
        }

        @Test
        fun `Matcher filtertekst i verdi uten treff i nøkkelen`() {
            // Arrange
            val records = sampleRecords

            // Act
            val filtered = filterRecords(KafkaAdminController.RecordFilter("needle"), records)

            // Assert
            assertEquals(listOf("value needle"), filtered.map { it.value })
        }

        @Test
        fun `Null nøkkel og verdi matcher ikke en ikke-blank filtertekst`() {
            // Arrange
            val records = listOf(sampleRecords[1])

            // Act
            val filtered = filterRecords(KafkaAdminController.RecordFilter("needle"), records)

            // Assert
            assertTrue(filtered.isEmpty())
        }
    }

    private companion object {
        val sampleRecords = listOf(
            KafkaRecord(0, "", "", emptyList(), 123, 123),
            KafkaRecord(0, null, null, emptyList(), 123, 124),
            KafkaRecord(0, "key123", "value123", emptyList(), 123, 125),
            KafkaRecord(0, "key1234", "value needle", emptyList(), 123, 126),
            KafkaRecord(0, "key9876", "value9876", emptyList(), 123, 127),
        )
    }
}
