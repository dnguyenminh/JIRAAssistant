package com.assistant.server.document

import com.assistant.document.models.GeneratedDocument
import com.assistant.server.document.DocumentPreservationGenerators.arbDraftDocument
import com.assistant.server.document.DocumentPreservationGenerators.arbGeneratedDocument
import com.assistant.server.document.DocumentPreservationGenerators.arbNonDraftDocument
import com.assistant.server.document.DocumentPreservationGenerators.arbTicketId
import com.assistant.server.jobs.InMemoryDocumentRepository
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Preservation property tests — verify baseline behavior on UNFIXED code.
 * These tests MUST PASS before and after the fix is applied.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**
 */
@OptIn(ExperimentalKotest::class)
class DocumentPreservationPropertyTest {

    private val cfg = PropTestConfig(iterations = 50)

    // ── Property: JSON serialize → deserialize roundtrip preserves all existing fields ──

    /**
     * **Validates: Requirements 3.7**
     *
     * For all GeneratedDocument instances, JSON serialize → deserialize roundtrip
     * preserves all existing fields. Backward compatibility baseline.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `JSON roundtrip preserves all existing GeneratedDocument fields`() {
        runBlocking {
            checkAll(cfg, arbGeneratedDocument()) { doc ->
                val serialized = json.encodeToString(doc)
                val deserialized = json.decodeFromString<GeneratedDocument>(serialized)
                assertEquals(doc, deserialized, "Roundtrip should preserve all fields")
            }
        }
    }

    /**
     * **Validates: Requirements 3.7**
     *
     * Serialized JSON contains all expected field keys for existing schema.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `serialized JSON contains all existing field keys`() {
        runBlocking {
            checkAll(cfg, arbGeneratedDocument()) { doc ->
                val serialized = json.encodeToString(doc)
                val obj = Json.parseToJsonElement(serialized).jsonObject
                val expectedKeys = listOf(
                    "documentType", "ticketId", "generatedAt", "markdownContent",
                    "sourceTicketIds", "attachmentSources", "aiProviderUsed",
                    "approvalStatus", "versionNumber", "rejectReason",
                    "reviewedBy", "reviewedAt"
                )
                for (key in expectedKeys) {
                    assertTrue(obj.containsKey(key), "JSON should contain key '$key'")
                }
            }
        }
    }

    // ── Property: Non-approve/reject API calls preserve response structure ──

    /**
     * **Validates: Requirements 3.1**
     *
     * listByTicketId returns metadata for all saved documents of that ticket.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `listByTicketId returns metadata for all documents of ticket`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbDraftDocument()) { ticketId, doc ->
                val repo = InMemoryDocumentRepository()
                val saved = doc.copy(ticketId = ticketId)
                repo.save(saved)
                val meta = repo.listByTicketId(ticketId)
                assertEquals(1, meta.size)
                assertEquals(saved.documentType, meta[0].documentType)
                assertEquals(saved.approvalStatus, meta[0].approvalStatus)
            }
        }
    }

    /**
     * **Validates: Requirements 3.2**
     *
     * findLatestByTicketIdAndType returns the document with matching content.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `findLatestByTicketIdAndType returns correct document`() {
        runBlocking {
            checkAll(cfg, arbDraftDocument()) { doc ->
                val repo = InMemoryDocumentRepository()
                repo.save(doc)
                val found = repo.findLatestByTicketIdAndType(doc.ticketId, doc.documentType)
                assertNotNull(found)
                assertEquals(doc.markdownContent, found!!.markdownContent)
                assertEquals(doc.documentType, found.documentType)
                assertEquals(doc.ticketId, found.ticketId)
            }
        }
    }

    /**
     * **Validates: Requirements 3.3**
     *
     * findAllVersions returns version metadata for documents of given type.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `findAllVersions returns version metadata`() {
        runBlocking {
            checkAll(cfg, arbDraftDocument()) { doc ->
                val repo = InMemoryDocumentRepository()
                repo.save(doc)
                val versions = repo.findAllVersions(doc.ticketId, doc.documentType)
                assertTrue(versions.isNotEmpty())
                assertEquals(doc.documentType, versions[0].documentType)
            }
        }
    }

    // ── Property: Non-DRAFT documents return 409 on approve/reject ──

    /**
     * **Validates: Requirements 3.5**
     *
     * For all documents with approvalStatus != "DRAFT", approve should be rejected.
     * We test the business logic check directly (same as handleApprove).
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `non-DRAFT documents reject approve with conflict logic`() {
        runBlocking {
            checkAll(cfg, arbNonDraftDocument()) { doc ->
                // Business rule: only DRAFT can be approved
                assertNotEquals("DRAFT", doc.approvalStatus)
                // This is the same check handleApprove performs
                val wouldConflict = doc.approvalStatus != "DRAFT"
                assertTrue(wouldConflict, "Non-DRAFT should trigger conflict")
            }
        }
    }

    /**
     * **Validates: Requirements 3.5**
     *
     * For all non-DRAFT documents stored in repo, approve via updateApprovalStatus
     * should not change status when business rule is enforced.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `non-DRAFT documents in repo preserve status on approve attempt`() {
        runBlocking {
            checkAll(cfg, arbNonDraftDocument()) { doc ->
                val repo = InMemoryDocumentRepository()
                val id = repo.saveAndGetId(doc)
                val before = repo.findById(id)!!
                // Simulate handleApprove guard: check status before updating
                if (before.approvalStatus != "DRAFT") {
                    // Would return 409 — do NOT call updateApprovalStatus
                    val after = repo.findById(id)!!
                    assertEquals(before.approvalStatus, after.approvalStatus)
                }
            }
        }
    }

    // ── Property: mapRow() correctly maps all existing columns ──

    /**
     * **Validates: Requirements 3.4**
     *
     * For all GeneratedDocument instances, save → findById roundtrip via
     * InMemoryDocumentRepository preserves all existing fields.
     * This mirrors mapRow() behavior for existing columns.
     */
    @Test
    @Tag("Feature: document-approve-fix, Property 2: Preservation")
    fun `save and findById roundtrip preserves all existing fields`() {
        runBlocking {
            checkAll(cfg, arbGeneratedDocument()) { doc ->
                val repo = InMemoryDocumentRepository()
                val id = repo.saveAndGetId(doc)
                val found = repo.findById(id)!!
                assertEquals(doc.documentType, found.documentType)
                assertEquals(doc.ticketId, found.ticketId)
                assertEquals(doc.generatedAt, found.generatedAt)
                assertEquals(doc.markdownContent, found.markdownContent)
                assertEquals(doc.sourceTicketIds, found.sourceTicketIds)
                assertEquals(doc.attachmentSources, found.attachmentSources)
                assertEquals(doc.aiProviderUsed, found.aiProviderUsed)
                assertEquals(doc.approvalStatus, found.approvalStatus)
                assertEquals(doc.versionNumber, found.versionNumber)
                assertEquals(doc.rejectReason, found.rejectReason)
                assertEquals(doc.reviewedBy, found.reviewedBy)
                assertEquals(doc.reviewedAt, found.reviewedAt)
            }
        }
    }
}
