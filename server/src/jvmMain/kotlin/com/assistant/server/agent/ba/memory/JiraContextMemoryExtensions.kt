package com.assistant.server.agent.ba.memory

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.SlotFullResult
import com.assistant.agent.memory.StructuredMemory
import java.time.Instant

/**
 * Extension functions for typed access to JiraContextMemory slots.
 * Each function wraps StructuredMemory.store() with proper
 * MemoryEntry metadata (source, toolName, timestamp).
 */

fun StructuredMemory.storeSummary(
    text: String, source: String, toolName: String
): SlotFullResult? = store(
    "summary",
    MemoryEntry(text, source, toolName, Instant.now().toString())
)

fun StructuredMemory.storeComment(
    comment: String, source: String
): SlotFullResult? = store(
    "comments",
    MemoryEntry(comment, source, "fetchComments", Instant.now().toString())
)

fun StructuredMemory.storeLinkedTicket(
    ticketId: String, summary: String
): SlotFullResult? = store(
    "linkedTickets",
    MemoryEntry("$ticketId: $summary", ticketId, "getLinkedIssues", Instant.now().toString())
)

fun StructuredMemory.getLinkedTicketIds(): List<String> =
    getSlot("linkedTickets").map { it.source }

fun StructuredMemory.hasKBRecord(ticketId: String): Boolean =
    getSlot("kbRecords").any { it.source == ticketId }

fun StructuredMemory.storeAttachmentData(
    content: String, source: String
): SlotFullResult? = store(
    "attachmentsData",
    MemoryEntry(content, source, "processAttachment", Instant.now().toString())
)

fun StructuredMemory.storeKBRecord(
    record: String, ticketId: String
): SlotFullResult? = store(
    "kbRecords",
    MemoryEntry(record, ticketId, "lookupKBRecord", Instant.now().toString())
)

fun StructuredMemory.storeTechnicalDetails(
    details: String, source: String
): SlotFullResult? = store(
    "technicalDetails",
    MemoryEntry(details, source, "fetchJiraDetails", Instant.now().toString())
)

fun StructuredMemory.storeBusinessGoals(
    goals: String, source: String
): SlotFullResult? = store(
    "businessGoals",
    MemoryEntry(goals, source, "fetchJiraDetails", Instant.now().toString())
)

fun StructuredMemory.storeAcceptanceCriteria(
    criteria: String, source: String
): SlotFullResult? = store(
    "acceptanceCriteria",
    MemoryEntry(criteria, source, "fetchJiraDetails", Instant.now().toString())
)
