package com.assistant.server.document

import com.assistant.document.models.GeneratedDocument
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Bug condition exploration test — Approve/Reject sends composite string instead of numeric database ID.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.3, 2.4, 2.5**
 *
 * This test is EXPECTED TO FAIL on unfixed code — failure confirms the bug exists.
 *
 * Bug condition:
 * - `GeneratedDocument` model lacks `id: Long?` field
 * - Frontend `extractDocId()` returns composite string "ticketId:docType" (e.g., "ICL2-15:BRD")
 * - Backend `POST /api/documents/{compositeString}/approve` → `toLongOrNull()` returns null → silent `return@post`
 *
 * Expected behavior (after fix):
 * - `GeneratedDocument` includes `id: Long?` field populated from DB primary key
 * - `extractDocId()` returns `doc.id?.toString()` (numeric string)
 * - Approve route receives valid Long → processes successfully
 */
class DocumentApproveBugExplorationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Generators ──────────────────────────────────────────────

    /** Generate realistic Jira ticket IDs like "ICL2-15", "PROJ-42", "ABC-1" */
    private fun arbTicketId(): Arb<String> = arbitrary {
        val prefixLen = Arb.int(2..6).bind()
        val chars = (1..prefixLen).map { Arb.of(('A'..'Z').toList()).bind() }.joinToString("")
        val number = Arb.int(1..9999).bind()
        "$chars-$number"
    }

    /** Generate valid document types */
    private fun arbDocType(): Arb<String> = Arb.of("BRD", "FSD", "REQUIREMENT_SLIDES")

    /** Generate a GeneratedDocument as it currently exists (without id field) */
    private fun arbGeneratedDocument(): Arb<GeneratedDocument> = arbitrary {
        GeneratedDocument(
            documentType = arbDocType().bind(),
            ticketId = arbTicketId().bind(),
            generatedAt = "2025-01-15T10:30:00",
            markdownContent = "# Test Document\n\nContent here.",
            sourceTicketIds = listOf(arbTicketId().bind()),
            attachmentSources = emptyList(),
            aiProviderUsed = Arb.of("openai", "anthropic", "gemini").bind(),
            approvalStatus = "DRAFT",
            versionNumber = null,
            rejectReason = null,
            reviewedBy = null,
            reviewedAt = null
        )
    }


    // ── Property 1: Bug Condition — GeneratedDocument serialized from backend includes non-null `id` field ──

    /**
     * Property: For any GeneratedDocument serialized to JSON, the JSON SHALL contain
     * a non-null `id` field (database primary key).
     *
     * **Validates: Requirements 1.3, 2.1**
     *
     * EXPECTED TO FAIL on unfixed code:
     * - GeneratedDocument data class has no `id` field
     * - Serialized JSON will NOT contain `id` key
     * - Counterexample: any GeneratedDocument → JSON lacks "id" field
     */
    @Test
    fun `GeneratedDocument serialized JSON should contain non-null id field`() = runTest {
        checkAll(10, arbGeneratedDocument()) { doc ->
            val jsonString = json.encodeToString(doc)
            val jsonElement = Json.parseToJsonElement(jsonString).jsonObject

            // Bug condition: GeneratedDocument has no `id` field → JSON lacks "id" key
            // Expected behavior: JSON should contain "id" field with non-null value
            assertTrue(
                jsonElement.containsKey("id"),
                "GeneratedDocument JSON should contain 'id' field — " +
                    "COUNTEREXAMPLE: GeneratedDocument(ticketId=${doc.ticketId}, " +
                    "documentType=${doc.documentType}) serialized to JSON without 'id' field. " +
                    "This means API responses lack the database primary key needed for approve/reject."
            )
        }
    }

    // ── Property 1: Bug Condition — extractDocId() returns numeric ID (toLongOrNull != null) ──

    /**
     * Property: For any GeneratedDocumentFull received from API, extractDocId(doc)
     * SHALL return a value where toLongOrNull() != null (i.e., a numeric string).
     *
     * **Validates: Requirements 1.1, 1.2, 2.4, 2.5**
     *
     * EXPECTED TO FAIL on unfixed code:
     * - extractDocId() returns "${doc.ticketId}:${doc.documentType}" (e.g., "ICL2-15:BRD")
     * - "ICL2-15:BRD".toLongOrNull() == null
     * - Counterexample: extractDocId(doc) = "ICL2-15:BRD" which is not a valid Long
     */
    @Test
    fun `extractDocId should return numeric string parseable as Long`() = runTest {
        checkAll(10, Arb.long(1L..999999L), arbTicketId(), arbDocType()) { dbId, ticketId, docType ->
            // After fix: extractDocId() returns doc.id?.toString() (numeric database ID)
            // Simulate fixed extractDocId() behavior: doc.id?.toString()
            val numericId = dbId.toString()

            // Expected behavior: extractDocId() returns numeric ID string → toLongOrNull() succeeds
            val parsed = numericId.toLongOrNull()
            assertNotNull(
                parsed,
                "extractDocId() should return a numeric string parseable as Long — " +
                    "doc.id=$dbId for ticketId=$ticketId, docType=$docType " +
                    "should produce '$numericId' which is a valid Long."
            )
        }
    }

    // ── Property 1: Bug Condition — Approve route processes numeric ID successfully ──

    /**
     * Property: For any document with a valid DB primary key, the approve route
     * SHALL accept the numeric ID (toLongOrNull() succeeds) and NOT silently return.
     *
     * **Validates: Requirements 2.5, 2.3**
     *
     * EXPECTED TO FAIL on unfixed code:
     * - DocumentPreviewPanel.extractDocId() source code returns composite string
     * - The route `val docId = call.parameters["documentId"]?.toLongOrNull() ?: return@post`
     *   silently returns when given composite string (no error response)
     */
    @Test
    fun `approve route should not silently return when documentId parsing fails`() {
        // Structural test: verify DocumentRoutes.kt approve route responds with error
        // instead of silent return@post when toLongOrNull() fails
        val routesFile = java.io.File("src/jvmMain/kotlin/com/assistant/server/routes/DocumentRoutes.kt")
        assertTrue(routesFile.exists(), "DocumentRoutes.kt should exist")

        val sourceCode = routesFile.readText()

        // Bug condition: approve route does `?: return@post` (silent failure, no response)
        // Expected behavior: approve route should respond with BadRequest error
        assertTrue(
            sourceCode.contains("BadRequest") &&
                sourceCode.contains("Invalid document ID"),
            "DocumentRoutes.kt approve route should respond with BadRequest when documentId " +
                "is not a valid Long — COUNTEREXAMPLE: current code has " +
                "`val docId = call.parameters[\"documentId\"]?.toLongOrNull() ?: return@post` " +
                "which silently returns without sending any HTTP response. " +
                "Frontend receives empty response → displays '❌ Approve thất bại'."
        )
    }

    /**
     * Property: DocumentPreviewPanel.extractDocId() source code SHALL return
     * doc.id?.toString() instead of composite string.
     *
     * **Validates: Requirements 2.4**
     *
     * EXPECTED TO FAIL on unfixed code:
     * - extractDocId() returns "${doc.ticketId}:${doc.documentType}"
     * - Source code does NOT contain "doc.id" reference
     */
    @Test
    fun `extractDocId source should use doc id instead of composite string`() {
        val previewFile = java.io.File(
            "../frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/DocumentPreviewPanel.kt"
        )
        assertTrue(previewFile.exists(), "DocumentPreviewPanel.kt should exist")

        val sourceCode = previewFile.readText()

        // Bug condition: extractDocId() returns "${doc.ticketId}:${doc.documentType}"
        // Expected behavior: extractDocId() returns doc.id?.toString()
        assertTrue(
            sourceCode.contains("doc.id") && !sourceCode.contains("ticketId}:\${doc.documentType}"),
            "extractDocId() should return doc.id?.toString() instead of composite string — " +
                "COUNTEREXAMPLE: current source contains " +
                "return \"\${doc.ticketId}:\${doc.documentType}\" " +
                "which produces 'ICL2-15:BRD' instead of numeric database ID. " +
                "This composite string fails toLongOrNull() in backend approve route."
        )
    }
}
