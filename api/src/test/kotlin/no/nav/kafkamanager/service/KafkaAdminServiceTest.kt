package no.nav.kafkamanager.service

import no.nav.kafkamanager.controller.KafkaAdminController
import no.nav.kafkamanager.domain.KafkaRecord
import no.nav.kafkamanager.service.KafkaAdminService.Companion.filterRecords
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KafkaAdminServiceTest {

    private val records = listOf(
        KafkaRecord( 
            key = "",
            value = "",
            headers = emptyList(),
            timestamp = 123,
            offset = 123
        ),
        KafkaRecord(
            key = null,
            value = null,
            headers = emptyList(),
            timestamp = 123,
            offset = 124
        ),
        KafkaRecord(
            key = "key123",
            value = "value123",
            headers = emptyList(),
            timestamp = 123,
            offset = 125
        ),
        KafkaRecord(
            key = "key1234",
            value = "value1234",
            headers = emptyList(),
            timestamp = 123,
            offset = 126
        ),
        KafkaRecord(
            key = "key9876",
            value = "value9876",
            headers = emptyList(),
            timestamp = 123,
            offset = 127
        )
    )

    @Test
    fun `filterRecords should not filter with no filter`() {
        assertEquals(records, filterRecords(null, records))
    }
    
    @Test
    fun `filterRecords should filter records with key`() {
        val filter = KafkaAdminController.RecordFilter(
            keyContains = "123",
            valueContains = null
        )

        val filteredRecords = filterRecords(filter, records)
        assertEquals(2, filteredRecords.size)

        filteredRecords.forEach {
            assertTrue(it.key!!.contains("123"))
        }
    }

    @Test
    fun `filterRecords should filter records with value`() {
        val filter = KafkaAdminController.RecordFilter(
            keyContains = null,
            valueContains = "123"
        )

        val filteredRecords = filterRecords(filter, records)
        assertEquals(2, filteredRecords.size)

        filteredRecords.forEach {
            assertTrue(it.value!!.contains("123"))
        }
    }


}