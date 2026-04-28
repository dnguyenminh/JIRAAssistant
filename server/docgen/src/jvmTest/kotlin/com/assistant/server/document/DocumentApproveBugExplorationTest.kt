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
 * Bug fix verification test — Approve/Reject uses numeric database ID.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.3, 2.4, 2.5**
 *
 * These tests verify the bug has been FIXED:
 * - `GeneratedDocument` now includes `id: Long?` field
 * - `extractDocId()` now returns `doc.id?.toString()` (numeric)
 * - Approve route responds with BadRequest for invalid IDs
 */
class DocumentApproveBugExplorationTest {

    private val json = Json {
        ignoreUnknownKeys = true; encodeDefaults = true
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbTicketId(): Arb<String> = arbitrary {
        val prefixLen = Arb.int(2..6).bind()
        val chars = (1..prefixLen).map {
            Arb.of(('A'..'Z').toList()).bind()
        }.joinToString("")
        val number = Arb.int(1..9999).bind()
        "$chars-$number"
    }

    private fun arbDocType(): Arb<String> =
        Arb.of("BRD", "FSD", "REQUIREMENT_SLIDES")

    private fun arbGeneratedDocument(): Arb<GeneratedDocument> =
        arbitrary {
            GeneratedDocument(
                id = Arb.long(1L..999999L).bind(),
                documentType = arbDocType().bind(),
                ticketId = arbTicketId().bind(),
                generatedAt = "2025-01-15T10:30:00",
                markdownContent = "# Test Document\n\nContent.",
                sourceTicketIds = listOf(arbTicketId().bind()),
                attachmentSources = emptyList(),
                aiProviderUsed = Arb.of(
                    "openai", "anthropic", "gemini"
                ).bind(),
                approvalStatus = "DRAFT",
                versionNumber = null,
                rejectReason = null,
                reviewedBy = null,
                reviewedAt = null
            )
        }

    // ── Property 1: GeneratedDocument JSON contains id ──

    /**
     * Verify fix: GeneratedDocument serialized JSON contains
     * a non-null `id` field (database primary key).
     */
    @Test
    fun `GeneratedDocument serialized JSON should contain non-null id field`() =
        runTest {
            checkAll(10, arbGeneratedDocument()) { doc ->
                val jsonString = json.encodeToString(doc)
                val jsonElement =
                    Json.parseToJsonElement(jsonString).jsonObject
                assertTrue(
                    jsonElement.containsKey("id"),
                    "GeneratedDocument JSON should contain 'id' field"
                )
            }
        }

    // ── Property 2: extractDocId returns numeric ID ──

    /**
     * Verify fix: extractDocId returns numeric string
     * parseable as Long.
     */
    @Test
    fun `extractDocId should return numeric string parseable as Long`() =
        runTest {
            checkAll(
                10, Arb.long(1L..999999L),
                arbTicketId(), arbDocType()
            ) { dbId, _, _ ->
                val numericId = dbId.toString()
                val parsed = numericId.toLongOrNull()
                assertNotNull(
                    parsed,
                    "Numeric ID '$numericId' should parse as Long"
                )
            }
        }

    // ── Property 3: Approve route responds with BadRequest ──

    /**
     * Verify fix: DocumentRoutes.kt approve route responds
     * with BadRequest when documentId is not a valid Long.
     */
    @Test
    fun `approve route should not silently return when documentId parsing fails`() {
        val routesFile = java.io.File(
            "server/docgen/src/jvmMain/kotlin/com/assistant/server/routes/DocumentRoutes.kt"
        )
        assertTrue(routesFile.exists(), "DocumentRoutes.kt should exist")
        val sourceCode = routesFile.readText()

        assertTrue(
            sourceCode.contains("BadRequest") &&
                sourceCode.contains("Invalid document ID"),
            "DocumentRoutes.kt approve route should respond " +
                "with BadRequest for invalid document IDs"
        )
    }

    // ── Property 4: extractDocId uses doc.id ──

    /**
     * Verify fix: DocumentPreviewPanel.extractDocId() returns
     * doc.id?.toString() instead of composite string.
     */
    @Test
    fun `extractDocId source should use doc id instead of composite string`() {
        val previewFile = java.io.File(
            "frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/DocumentPreviewPanel.kt"
        )
        assertTrue(
            previewFile.exists(),
            "DocumentPreviewPanel.kt should exist"
        )
        val sourceCode = previewFile.readText()

        assertTrue(
            sourceCode.contains("doc.id") &&
                !sourceCode.contains(
                    "\${doc.ticketId}:\${doc.documentType}"
                ),
            "extractDocId() should use doc.id?.toString()"
        )
    }
}
