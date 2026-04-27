package com.assistant.server.agent.ba.prompt

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.server.document.curation.models.ContentClassification

/**
 * Builds TO-BE / AS-IS / OUTDATED sections from classified memory.
 *
 * Reads ticketClassifications slot to determine which tickets
 * belong in which section. Falls back to including all tickets
 * in context when no classifications exist.
 *
 * Requirements: 3.2, 3.3, 3.4, 3.5, 3.6
 */
internal object MasterPromptContextBuilder {

    fun buildToBeSection(
        memory: StructuredMemory,
        kbSources: Set<String>
    ): String {
        val sb = StringBuilder("### TO-BE (New Requirements)\n")
        val toBeIds = getTicketIdsByClassification(
            memory, ContentClassification.TO_BE
        )
        appendRootTicketData(sb, memory, kbSources)
        appendClassifiedTickets(sb, memory, kbSources, toBeIds)
        return sb.toString().trimEnd()
    }

    fun buildAsIsSection(
        memory: StructuredMemory,
        kbSources: Set<String>
    ): String {
        val asIsIds = getTicketIdsByClassification(
            memory, ContentClassification.AS_IS
        )
        if (asIsIds.isEmpty()) return ""
        val sb = StringBuilder("### AS-IS (Existing Functionality)\n")
        appendClassifiedTickets(sb, memory, kbSources, asIsIds)
        return sb.toString().trimEnd()
    }

    fun buildOutdatedMetadata(memory: StructuredMemory): String {
        val outdated = getOutdatedEntries(memory)
        if (outdated.isEmpty()) return ""
        val sb = StringBuilder("### OUTDATED (Superseded)\n")
        outdated.forEach { entry ->
            sb.append("- ${entry.source}: superseded\n")
        }
        return sb.toString().trimEnd()
    }

    fun hasClassifications(memory: StructuredMemory): Boolean =
        memory.getSlot("ticketClassifications").isNotEmpty()

    private fun getTicketIdsByClassification(
        memory: StructuredMemory,
        classification: ContentClassification
    ): Set<String> {
        return memory.getSlot("ticketClassifications")
            .filter { it.data.contains(classification.name) }
            .map { it.source }
            .toSet()
    }

    private fun getOutdatedEntries(
        memory: StructuredMemory
    ): List<MemoryEntry> {
        return memory.getSlot("ticketClassifications")
            .filter { it.data.contains("OUTDATED") }
    }

    private fun appendRootTicketData(
        sb: StringBuilder,
        memory: StructuredMemory,
        kbSources: Set<String>
    ) {
        val summaryEntries = memory.getSlot("summary")
        summaryEntries.forEach { entry ->
            sb.append("${entry.data} [Source: ${entry.source}/summary]\n")
        }
        appendKbEntries(sb, memory, kbSources)
    }

    private fun appendKbEntries(
        sb: StringBuilder,
        memory: StructuredMemory,
        kbSources: Set<String>
    ) {
        memory.getSlot("kbRecords")
            .filter { it.source in kbSources || kbSources.isEmpty() }
            .forEach { entry ->
                sb.append("${entry.data} [Source: ${entry.source}/kbRecords]\n")
            }
    }

    private fun appendClassifiedTickets(
        sb: StringBuilder,
        memory: StructuredMemory,
        kbSources: Set<String>,
        ticketIds: Set<String>
    ) {
        val linkedEntries = memory.getSlot("linkedTickets")
            .filter { it.source in ticketIds }
        linkedEntries.forEach { entry ->
            if (entry.source !in kbSources) {
                sb.append("${entry.data} [Source: ${entry.source}/linkedTickets]\n")
            }
        }
    }
}
