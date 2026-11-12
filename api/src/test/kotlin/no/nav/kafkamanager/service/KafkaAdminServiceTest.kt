package no.nav.kafkamanager.service

import no.nav.common.utils.Credentials
import no.nav.kafkamanager.config.EnvironmentProperties
import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.AppConfig
import no.nav.kafkamanager.domain.AvailableTopic
import no.nav.kafkamanager.domain.DeserializerType
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.domain.TopicConfig
import no.nav.kafkamanager.domain.TopicLocation
import no.nav.kafkamanager.service.KafkaAdminService.Companion.filterRecords
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class KafkaAdminServiceTest {
    @Test
    fun `filterRecords should not filter with null filter`() {
        assertEquals(records, filterRecords(null, records))
    }

    @Test
    fun `filterRecords should not filter with empty filter`() {
        assertEquals(records, filterRecords(KafkaAdminController.RecordFilter(text = ""), records))
        assertEquals(records, filterRecords(KafkaAdminController.RecordFilter(text = null), records))
    }

    @Test
    fun `filterRecords should filter records with key`() {
        val filter = KafkaAdminController.RecordFilter(text = "123")

        val filteredRecords = filterRecords(filter, records)
        assertEquals(2, filteredRecords.size)

        filteredRecords.forEach {
            assertTrue(it.key!!.contains("123"))
        }
    }

    @Test
    fun `filterRecords should filter records with value`() {
        val filter = KafkaAdminController.RecordFilter(text = "123")

        val filteredRecords = filterRecords(filter, records)
        assertEquals(2, filteredRecords.size)

        filteredRecords.forEach {
            assertTrue(it.value!!.contains("123"))
        }
    }

    @Test
    fun `filterRecords should filter records with text with whitespace`() {
        val filter = KafkaAdminController.RecordFilter(text = "key 123")

        val filteredRecords = filterRecords(filter, records)
        assertEquals(2, filteredRecords.size)
    }

    @Test
    fun `filterRecords should filter records with text with different letter case`() {
        val filter = KafkaAdminController.RecordFilter(text = "KeY123")

        val filteredRecords = filterRecords(filter, records)
        assertEquals(2, filteredRecords.size)
    }

    @Test
    fun `available topics should expose record deletion support`() {
        assertEquals(
            listOf(
                AvailableTopic("aiven-topic", supportsRecordDeletion = true),
                AvailableTopic("on-prem-topic", supportsRecordDeletion = false),
            ),
            kafkaAdminService.getAvailableTopics(),
        )
    }

    @Test
    fun `record deletion should reject on-prem topics`() {
        val exception =
            assertThrows(ResponseStatusException::class.java) {
                kafkaAdminService.deleteRecords("on-prem-topic", 0, 1)
            }

        assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
        assertEquals("Kafka admin operations are not supported for this topic", exception.reason)
    }

    @Test
    fun `consumer group lookup should use operation-neutral error for on-prem topics`() {
        val exception =
            assertThrows(ResponseStatusException::class.java) {
                kafkaAdminService.getConsumerGroups(KafkaAdminController.GetConsumerGroupsRequest("on-prem-topic"))
            }

        assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.statusCode)
        assertEquals("Kafka admin operations are not supported for this topic", exception.reason)
    }

    private companion object {
        val kafkaAdminService =
            KafkaAdminService(
                EnvironmentProperties(),
                AppConfig(
                    topics =
                        listOf(
                            TopicConfig(
                                name = "aiven-topic",
                                location = TopicLocation.AIVEN,
                                keyDeserializerType = DeserializerType.STRING,
                                valueDeserializerType = DeserializerType.STRING,
                                isolationLevel = null,
                            ),
                            TopicConfig(
                                name = "on-prem-topic",
                                location = TopicLocation.ON_PREM,
                                keyDeserializerType = DeserializerType.STRING,
                                valueDeserializerType = DeserializerType.STRING,
                                isolationLevel = null,
                            ),
                        ),
                ),
            ) { Credentials("", "") }

        val records =
            listOf(
                KafkaRecord(
                    partition = 0,
                    key = "",
                    value = "",
                    headers = emptyList(),
                    timestamp = 123,
                    offset = 123,
                ),
                KafkaRecord(
                    partition = 0,
                    key = null,
                    value = null,
                    headers = emptyList(),
                    timestamp = 123,
                    offset = 124,
                ),
                KafkaRecord(
                    partition = 0,
                    key = "key123",
                    value = "value123",
                    headers = emptyList(),
                    timestamp = 123,
                    offset = 125,
                ),
                KafkaRecord(
                    partition = 0,
                    key = "key1234",
                    value = "value1234",
                    headers = emptyList(),
                    timestamp = 123,
                    offset = 126,
                ),
                KafkaRecord(
                    partition = 0,
                    key = "key9876",
                    value = "value9876",
                    headers = emptyList(),
                    timestamp = 123,
                    offset = 127,
                ),
            )
    }
}
