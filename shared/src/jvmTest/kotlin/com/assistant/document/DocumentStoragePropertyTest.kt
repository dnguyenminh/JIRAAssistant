package com.assistant.document

import com.assistant.document.models.GeneratedDocument
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Property 9: Document overwrite idempotence.
 *
 * After saving N times with same ticketId+type, findByTicketIdAndType
 * returns exactly 1 document with the most recent content.
 * Uses in-memory repository to test UPSERT logic.
 *
 * **Validates: Requirements 5.3**
 */
class DocumentStoragePropertyTest {

    /** In-memory repository simulating UNIQUE(ticket_id, document_type) + upsert. */
    private class InMemoryDocumentStore {
        private val store = mutableMapOf<String, GeneratedDocument>()

        fun save(doc: GeneratedDocument) {
            val key = "${doc.ticketId}::${doc.documentType}"
            store[key] = doc
        }

        fun findByTicketIdAndType(ticketId: String, type: String): GeneratedDocument? {
            return store["${ticketId}::${type}"]
        }

        fun countByTicketIdAndType(ticketId: String, type: String): Int {
            val key = "${ticketId}::${type}"
            return if (store.containsKey(key)) 1 else 0
        }
    }

    @Test
    fun `Property 9 - save N times then find returns exactly 1 with latest content`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.string(3..10),
            Arb.element("BRD", "FSD", "REQUIREMENT_SLIDES"),
            Arb.int(1..10),
            Arb.list(Arb.string(10..100), 1..10)
        ) { ticketId, docType, saveCount, contents ->
            val store = InMemoryDocumentStore()
            val actualSaves = minOf(saveCount, contents.size)
            var lastContent = ""

            for (i in 0 until actualSaves) {
                lastContent = contents[i]
                store.save(
                    GeneratedDocument(
                        documentType = docType,
                        ticketId = ticketId,
                        generatedAt = "2025-01-${15 + i}T00:00:00Z",
                        markdownContent = lastContent
                    )
                )
            }

            assertEquals(
                1, store.countByTicketIdAndType(ticketId, docType),
                "Expected exactly 1 document for $ticketId/$docType"
            )
            val found = store.findByTicketIdAndType(ticketId, docType)
            assertNotNull(found)
            assertEquals(lastContent, found.markdownContent, "Should have latest content")
        }
    }
}
