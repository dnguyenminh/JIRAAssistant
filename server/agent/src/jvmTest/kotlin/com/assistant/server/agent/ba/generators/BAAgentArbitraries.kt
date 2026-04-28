package com.assistant.server.agent.ba.generators

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Custom Arb generators for BA Agent property tests.
 */

private val ticketId = Arb.stringPattern("PROJ-[1-9][0-9]{0,4}")
private val safeText = Arb.string(1..200, Codepoint.alphanumeric())

/** All BA Agent tool names that store data into memory. */
val BA_TOOL_NAMES = listOf(
    "fetchJiraDetails",
    "getLinkedIssues",
    "fetchComments",
    "processAttachment",
    "lookupKBRecord",
    "searchKB"
)

/** Slot names from JiraContextMemorySchema. */
val JIRA_SLOT_NAMES = JiraContextMemorySchema.SLOTS.map { it.name }

fun Arb.Companion.baToolName(): Arb<String> =
    Arb.of(BA_TOOL_NAMES)

fun Arb.Companion.jiraSlotName(): Arb<String> =
    Arb.of(JIRA_SLOT_NAMES)

fun Arb.Companion.jiraTicketId(): Arb<String> = ticketId

fun Arb.Companion.baMemoryEntry(): Arb<MemoryEntry> = arbitrary {
    MemoryEntry(
        data = safeText.bind(),
        source = ticketId.bind(),
        toolName = Arb.baToolName().bind(),
        timestamp = Arb.string(10..30, Codepoint.alphanumeric()).bind()
    )
}

/** Generate a JiraContextMemory with 0–3 random entries per slot. */
fun Arb.Companion.jiraContextMemory(): Arb<StructuredMemory> =
    arbitrary {
        val memory = JiraContextMemorySchema.createMemory()
        for (slot in JiraContextMemorySchema.SLOTS) {
            val count = Arb.int(0..3).bind()
            repeat(count) {
                memory.store(slot.name, Arb.baMemoryEntry().bind())
            }
        }
        memory
    }

// --- Issue type values used across strategies ---
private val ISSUE_TYPES = listOf(
    "Story", "Epic", "Bug", "Task", "Subtask"
)

private val LABEL_POOL = listOf(
    "business", "requirement", "technical", "api",
    "backend", "frontend", "architecture", "database",
    "stakeholder", "executive", "goal", "devops"
)

private val RELATIONSHIP_TYPES = listOf(
    "blocks", "is-blocked-by", "relates-to",
    "duplicates", "clones"
)

private val FILE_EXTENSIONS = listOf(
    "doc", "docx", "pdf", "xlsx", "pptx", "png",
    "jpg", "jpeg", "gif", "svg", "bmp", "json",
    "yaml", "yml", "xml", "sql", "graphql", "proto",
    "md", "txt", "csv", "drawio", "puml", "ppt",
    "exe", "zip", "tar", "mp4", "avi", "log"
)

/**
 * Metadata about a linked ticket for relevance scoring tests.
 */
data class IssueMetadata(
    val issueType: String,
    val labels: List<String>,
    val components: List<String>,
    val relationshipType: String
)

/** Random issue metadata for relevance scoring tests. */
fun Arb.Companion.issueMetadata(): Arb<IssueMetadata> = arbitrary {
    IssueMetadata(
        issueType = Arb.of(ISSUE_TYPES).bind(),
        labels = Arb.list(Arb.of(LABEL_POOL), 0..4).bind(),
        components = Arb.list(safeText, 0..3).bind(),
        relationshipType = Arb.of(RELATIONSHIP_TYPES).bind()
    )
}

/**
 * Random issue metadata with fully arbitrary strings
 * (not limited to known values) for boundary testing.
 */
fun Arb.Companion.issueMetadataArbitrary(): Arb<IssueMetadata> =
    arbitrary {
        IssueMetadata(
            issueType = safeText.bind(),
            labels = Arb.list(safeText, 0..5).bind(),
            components = Arb.list(safeText, 0..3).bind(),
            relationshipType = safeText.bind()
        )
    }

/** Set of linked tickets with random metadata. */
fun Arb.Companion.linkedTicketSet(): Arb<List<IssueMetadata>> =
    Arb.list(Arb.issueMetadata(), 1..10)

/** Set of attachment filenames with random extensions. */
fun Arb.Companion.attachmentSet(): Arb<List<String>> = arbitrary {
    val count = Arb.int(1..10).bind()
    (1..count).map { buildFilename(it) }
}

private suspend fun io.kotest.property.arbitrary.ArbitraryBuilderContext.buildFilename(
    index: Int
): String {
    val name = safeText.bind()
    val ext = Arb.of(FILE_EXTENSIONS).bind()
    return "$name.$ext"
}

// Prompt-specific generators are in PromptArbitraries.kt

/** Random BA Agent phase name. */
fun Arb.Companion.agentPhaseName(): Arb<String> =
    Arb.of("collect", "expand", "visualize", "synthesize")

/** List of n random reasoning log entry strings. */
fun Arb.Companion.reasoningLogEntries(n: Int): Arb<List<String>> =
    Arb.list(safeText, n..n)
