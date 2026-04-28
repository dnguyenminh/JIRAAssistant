package com.assistant.server.agent.ba.generators

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Custom Arb generators for MasterPromptBuilder property tests.
 * Separated from BAAgentArbitraries to keep files under 200 lines.
 */

private val ticketId = Arb.stringPattern("PROJ-[1-9][0-9]{0,4}")
private val safeText = Arb.string(1..200, Codepoint.alphanumeric())
private val longText = Arb.string(500..2000, Codepoint.alphanumeric())

/**
 * Dense memory: fills each slot with large entries to stress
 * the prompt size limit.
 */
fun Arb.Companion.denseJiraContextMemory(): Arb<StructuredMemory> =
    arbitrary {
        val memory = JiraContextMemorySchema.createMemory()
        for (slot in JiraContextMemorySchema.SLOTS) {
            val count = Arb.int(1..5).bind()
            repeat(count) {
                val entry = MemoryEntry(
                    data = longText.bind(),
                    source = ticketId.bind(),
                    toolName = Arb.baToolName().bind(),
                    timestamp = "2024-01-01T00:00:00Z"
                )
                memory.store(slot.name, entry)
            }
        }
        memory
    }

/** Memory with at least 1 entry in summary slot. */
fun Arb.Companion.nonEmptyJiraContextMemory(): Arb<StructuredMemory> =
    arbitrary {
        val memory = JiraContextMemorySchema.createMemory()
        memory.store(
            "summary",
            MemoryEntry(
                data = safeText.bind(),
                source = ticketId.bind(),
                toolName = "fetchJiraDetails",
                timestamp = "2024-01-01T00:00:00Z"
            )
        )
        for (slot in JiraContextMemorySchema.SLOTS) {
            if (slot.name == "summary") continue
            val count = Arb.int(0..2).bind()
            repeat(count) {
                memory.store(
                    slot.name, Arb.baMemoryEntry().bind()
                )
            }
        }
        memory
    }

/**
 * Memory with both KB record AND raw description/comments
 * for the same ticket ID. Returns (memory, ticketId).
 */
fun Arb.Companion.memoryWithKBAndRaw():
    Arb<Pair<StructuredMemory, String>> = arbitrary {
    val memory = JiraContextMemorySchema.createMemory()
    val sharedTicket = ticketId.bind()
    storeRequiredSummary(memory, sharedTicket)
    storeKBRecord(memory, sharedTicket)
    storeRawDescription(memory, sharedTicket)
    storeRawComment(memory, sharedTicket)
    Pair(memory, sharedTicket)
}

private suspend fun ArbitraryBuilderContext.storeRequiredSummary(
    memory: StructuredMemory, ticket: String
) {
    memory.store(
        "summary",
        MemoryEntry(
            data = safeText.bind(), source = ticket,
            toolName = "fetchJiraDetails",
            timestamp = "2024-01-01T00:00:00Z"
        )
    )
}

private fun storeKBRecord(
    memory: StructuredMemory, ticket: String
) {
    memory.store(
        "kbRecords",
        MemoryEntry(
            data = "KB analysis for $ticket",
            source = ticket, toolName = "lookupKBRecord",
            timestamp = "2024-01-01T00:00:00Z"
        )
    )
}

private fun storeRawDescription(
    memory: StructuredMemory, ticket: String
) {
    memory.store(
        "description",
        MemoryEntry(
            data = "RAW_DESC_$ticket raw text",
            source = ticket, toolName = "fetchJiraDetails",
            timestamp = "2024-01-01T00:00:00Z"
        )
    )
}

private fun storeRawComment(
    memory: StructuredMemory, ticket: String
) {
    memory.store(
        "comments",
        MemoryEntry(
            data = "RAW_COMMENT_$ticket comment",
            source = ticket, toolName = "fetchComments",
            timestamp = "2024-01-01T00:00:00Z"
        )
    )
}
