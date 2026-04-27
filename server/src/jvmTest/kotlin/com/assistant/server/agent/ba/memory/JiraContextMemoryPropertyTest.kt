package com.assistant.server.agent.ba.memory

import com.assistant.agent.memory.MemoryEntry
import com.assistant.server.agent.ba.generators.*
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for JiraContextMemory (Property 1).
 *
 * Validates that all extension functions produce MemoryEntry
 * instances with complete provenance metadata.
 */
@OptIn(ExperimentalKotest::class)
class JiraContextMemoryPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 1: Memory entry metadata recording.
     *
     * For any data stored into any JiraContextMemory slot via
     * any BA Agent tool, the resulting MemoryEntry SHALL contain
     * a non-empty source (ticket ID), a non-empty toolName,
     * and a non-empty timestamp.
     *
     * **Validates: Requirements 1.2**
     */
    @Test
    @Tag("agent-document-generation")
    @Tag("Property-1")
    fun `stored entries have non-empty source toolName and timestamp`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.jiraSlotName(),
                Arb.baMemoryEntry()
            ) { slotName, entry ->
                val memory = JiraContextMemorySchema.createMemory()
                memory.store(slotName, entry)
                val stored = memory.getSlot(slotName)
                stored.forEach { verifyMetadata(it) }
            }
        }
    }

    /**
     * Property 1 (extension functions variant):
     * Each typed extension function produces entries with
     * non-empty source, toolName, and timestamp.
     *
     * **Validates: Requirements 1.2**
     */
    @Test
    @Tag("agent-document-generation")
    @Tag("Property-1")
    fun `extension functions produce entries with complete metadata`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.jiraTicketId(),
                Arb.string(1..200, Codepoint.alphanumeric())
            ) { ticketId, text ->
                verifyExtensionMetadata(ticketId, text)
            }
        }
    }

    private fun verifyExtensionMetadata(
        ticketId: String, text: String
    ) {
        val memory = JiraContextMemorySchema.createMemory()
        memory.storeSummary(text, ticketId, "fetchJiraDetails")
        memory.storeComment(text, ticketId)
        memory.storeLinkedTicket(ticketId, text)
        memory.storeAttachmentData(text, ticketId)
        memory.storeKBRecord(text, ticketId)
        memory.storeTechnicalDetails(text, ticketId)
        memory.storeBusinessGoals(text, ticketId)
        memory.storeAcceptanceCriteria(text, ticketId)

        for (slot in JiraContextMemorySchema.SLOTS) {
            memory.getSlot(slot.name).forEach { verifyMetadata(it) }
        }
    }

    private fun verifyMetadata(entry: MemoryEntry) {
        entry.source.shouldNotBeEmpty()
        entry.toolName.shouldNotBeEmpty()
        entry.timestamp.shouldNotBeEmpty()
    }
}
