package com.assistant.server.kb

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.db.JiraDatabase
import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.kb.KBRepositoryImpl
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 6: KB Record Persistence Round-Trip
 *
 * For any valid KBRecord, calling save(record) then findByTicketId(record.ticketId)
 * SHALL return a record with the same ticketId, requirementSummary, scrumPoints,
 * confidenceScore, rationale, and similarTicketRefs. Similarly, overwrite(newRecord)
 * then findByTicketId SHALL return the new record with updated timestamp.
 *
 * **Validates: Requirements 9.1, 9.4**
 *
 * Feature: jira-assistant-app, Property 6: KB Record Persistence Round-Trip
 */
@OptIn(ExperimentalKotest::class)
class KBRecordPersistencePropertyTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: JiraDatabase
    private lateinit var repository: KBRepository

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        database = JiraDatabase(driver)
        repository = KBRepositoryImpl(database)
    }

    // --- Generators ---

    private fun arbSafeString(min: Int = 1, max: Int = 30): Arb<String> =
        Arb.string(minSize = min, maxSize = max, codepoints = Codepoint.alphanumeric())

    private val arbEvolutionEntry: Arb<EvolutionEntry> = Arb.bind(
        arbSafeString(1, 10),
        arbSafeString(8, 10),
        arbSafeString(5, 50),
        Arb.element("ORIGIN", "UPDATE", "CURRENT")
    ) { version, date, description, changeType ->
        EvolutionEntry(version, date, description, changeType)
    }

    private val arbKBRecord: Arb<KBRecord> = Arb.bind(
        arbSafeString(3, 15),
        arbSafeString(10, 100),
        Arb.list(arbEvolutionEntry, 0..5),
        Arb.double(0.0, 40.0),
        Arb.double(0.0, 1.0),
        arbSafeString(5, 100),
        Arb.list(arbSafeString(3, 10), 0..5)
    ) { ticketId, summary, history, points, confidence, rationale, refs ->
        KBRecord(
            ticketId = ticketId,
            requirementSummary = summary,
            evolutionHistory = history,
            scrumPoints = points,
            confidenceScore = confidence,
            rationale = rationale,
            similarTicketRefs = refs,
            timestamp = "2024-01-01T00:00:00Z"
        )
    }

    @Test
    fun saveAndFindByTicketIdRoundTrip() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbKBRecord) { record ->
                // Clear table for each iteration
                driver.execute(null, "DELETE FROM kb_records", 0)

                val saved = repository.save(record)
                assertTrue(saved, "save() should return true")

                val found = repository.findByTicketId(record.ticketId)
                assertNotNull(found, "findByTicketId should return non-null after save")
                assertEquals(record.ticketId, found!!.ticketId)
                assertEquals(record.requirementSummary, found.requirementSummary)
                assertEquals(record.scrumPoints, found.scrumPoints, 0.001)
                assertEquals(record.confidenceScore, found.confidenceScore, 0.001)
                assertEquals(record.rationale, found.rationale)
                assertEquals(record.similarTicketRefs, found.similarTicketRefs)
                assertEquals(record.evolutionHistory, found.evolutionHistory)
            }
        }
    }

    @Test
    fun overwriteUpdatesRecord() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbKBRecord) { originalRecord ->
                driver.execute(null, "DELETE FROM kb_records", 0)

                repository.save(originalRecord)

                val updatedRecord = originalRecord.copy(
                    requirementSummary = originalRecord.requirementSummary + "_updated",
                    timestamp = "2024-06-15T12:00:00Z"
                )

                val overwritten = repository.overwrite(updatedRecord)
                assertTrue(overwritten, "overwrite() should return true")

                val found = repository.findByTicketId(originalRecord.ticketId)
                assertNotNull(found, "findByTicketId should return non-null after overwrite")
                assertEquals(updatedRecord.requirementSummary, found!!.requirementSummary)
                assertEquals(updatedRecord.timestamp, found.timestamp)
                assertEquals(updatedRecord.scrumPoints, found.scrumPoints, 0.001)
            }
        }
    }

    @Test
    fun findByTicketIdReturnsNullForMissing() {
        runBlocking {
            val result = repository.findByTicketId("NONEXISTENT-999")
            assertNull(result, "findByTicketId should return null for non-existent ticket")
        }
    }
}
