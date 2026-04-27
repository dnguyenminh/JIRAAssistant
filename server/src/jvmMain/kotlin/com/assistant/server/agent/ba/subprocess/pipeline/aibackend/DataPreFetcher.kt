package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.chat.LocalKBToolExecutor
import org.slf4j.LoggerFactory

/**
 * Code-driven data collection — fetches ticket data directly from
 * KBRepository and LocalKBToolExecutor (no MCP tool bridge, no AI).
 *
 * Flow: KB lookup main ticket → parse linked IDs → KB lookup each → KB search relationships.
 */
class DataPreFetcher(
    private val kbRepository: KBRepository,
    private val localKBToolExecutor: LocalKBToolExecutor?,
    private val jiraContentExtractor: com.assistant.ai.deepanalysis.JiraContentExtractor? = null,
    private val vectorStore: com.assistant.server.attachment.VectorStore? = null
) {
    private val log = LoggerFactory.getLogger(DataPreFetcher::class.java)

    suspend fun fetchAll(
        ticketId: String,
        iLog: PipelineInteractionLogger.InteractionLog? = null
    ): String {
        val data = StringBuilder()
        // 1. KB semantic search
        val kbSearch = searchKB(ticketId, iLog)
        if (kbSearch.isNotBlank()) data.appendLine("=== KB SEARCH ===\n$kbSearch\n")
        // 2. Main ticket from KBRepository
        val main = kbRepository.findByTicketId(ticketId)
        if (main != null) {
            iLog?.logToolCall("KBRepository.findByTicketId", mapOf("ticketId" to ticketId))
            data.appendLine("=== MAIN TICKET: $ticketId ===\n${formatRecord(main)}\n")
            iLog?.logToolResult("KBRepository.findByTicketId", true, data.length)
        } else {
            iLog?.logToolResult("KBRepository.findByTicketId", false, 0)
            log.warn("No KB record for main ticket {}", ticketId)
        }
        // 3. Parse linked ticket IDs from KB + Jira raw links
        val linkedIds = parseLinkedIds(main, kbSearch, ticketId)
        // Also get raw linked tickets from Jira if available
        val jiraLinkedIds = fetchJiraLinkedKeys(ticketId, iLog)
        val allLinkedIds = (linkedIds + jiraLinkedIds).distinct()
        log.info("Found {} linked tickets for {} (KB:{}, Jira:{}): {}", allLinkedIds.size, ticketId, linkedIds.size, jiraLinkedIds.size, allLinkedIds)
        // 4. Fetch each linked ticket from KB
        for (linked in allLinkedIds.take(MAX_LINKED)) {
            iLog?.logToolCall("KBRepository.findByTicketId", mapOf("ticketId" to linked))
            val record = kbRepository.findByTicketId(linked)
            if (record != null) {
                data.appendLine("=== LINKED: $linked ===\n${formatRecord(record)}\n")
                iLog?.logToolResult("KBRepository.findByTicketId[$linked]", true, formatRecord(record).length)
            } else {
                iLog?.logToolResult("KBRepository.findByTicketId[$linked]", false, 0)
            }
        }
        // 5. KB search relationships
        val rels = searchRelationships(ticketId, iLog)
        if (rels.isNotBlank()) data.appendLine("=== RELATIONSHIPS ===\n$rels\n")
        // 6. Search attachments for main ticket + linked tickets
        val attachmentTicketIds = listOf(ticketId) + allLinkedIds.take(MAX_ATTACHMENT_TICKETS)
        val attachments = searchAttachmentsMulti(attachmentTicketIds, iLog)
        if (attachments.isNotBlank()) data.appendLine("=== ATTACHMENTS ===\n$attachments\n")
        log.info("DataPreFetcher collected {} chars for {}", data.length, ticketId)
        return data.toString()
    }

    private suspend fun searchKB(query: String, iLog: PipelineInteractionLogger.InteractionLog?): String {
        val executor = localKBToolExecutor ?: return ""
        iLog?.logToolCall("search_knowledge", mapOf("query" to query))
        val result = executor.execute("search_knowledge", mapOf("query" to query))
        iLog?.logToolResult("search_knowledge", !result.startsWith("Tool error"), result.length)
        return if (result.startsWith("Tool error") || result.startsWith("No results")) "" else result
    }

    /** Fetch raw linked ticket keys directly from Jira via JiraContentExtractor. */
    private suspend fun fetchJiraLinkedKeys(ticketId: String, iLog: PipelineInteractionLogger.InteractionLog?): List<String> {
        val extractor = jiraContentExtractor ?: return emptyList()
        return try {
            iLog?.logToolCall("JiraContentExtractor.extract", mapOf("ticketId" to ticketId))
            val content = extractor.extract(ticketId)
            val keys = content.issueLinks.map { it.key } + content.subTasks.map { it.key } + listOfNotNull(content.parentKey.takeIf { it.isNotBlank() })
            iLog?.logToolResult("JiraContentExtractor.extract", true, keys.size)
            log.info("Jira raw links for {}: {}", ticketId, keys)
            keys.filter { it.isNotBlank() && it != ticketId }
        } catch (e: Exception) {
            log.warn("Failed to fetch Jira links for {}: {}", ticketId, e.message)
            emptyList()
        }
    }

    private suspend fun searchRelationships(query: String, iLog: PipelineInteractionLogger.InteractionLog?): String {
        val executor = localKBToolExecutor ?: return ""
        iLog?.logToolCall("search_relationships", mapOf("query" to query))
        val result = executor.execute("search_relationships", mapOf("query" to query))
        iLog?.logToolResult("search_relationships", !result.startsWith("Tool error"), result.length)
        return if (result.startsWith("Tool error") || result.startsWith("No ")) "" else result
    }

    /** Fetch attachments for multiple tickets, dedup by latest version. */
    private suspend fun searchAttachmentsMulti(
        ticketIds: List<String>,
        iLog: PipelineInteractionLogger.InteractionLog?
    ): String {
        val store = vectorStore ?: return ""
        val allChunks = collectChunksFromTickets(store, ticketIds)
        iLog?.logToolCall("VectorStore.attachments", mapOf("tickets" to ticketIds.joinToString(",")))
        val grouped = allChunks.groupBy { it.filename }
        val deduped = deduplicateVersions(grouped)
        iLog?.logToolResult("VectorStore.attachments", true, allChunks.size)
        iLog?.logAttachmentDetails(deduped.mapValues { it.value.size })
        if (deduped.isEmpty()) return ""
        return formatAttachmentResult(deduped)
    }

    private fun formatRecord(r: KBRecord): String = buildString {
        appendLine("Ticket: ${r.ticketId}")
        appendLine("Summary: ${r.requirementSummary}")
        if (r.businessSummary.isNotBlank()) appendLine("Business: ${r.businessSummary}")
        if (r.asIsState.isNotBlank()) appendLine("AS-IS: ${r.asIsState}")
        if (r.toBeState.isNotBlank()) appendLine("TO-BE: ${r.toBeState}")
        appendLine("Points: ${r.scrumPoints}, Confidence: ${r.confidenceScore}")
        appendLine("Rationale: ${r.rationale}")
        if (r.extractedRequirements.isNotEmpty()) appendLine("Requirements: ${r.extractedRequirements.joinToString("; ")}")
        if (r.dependencies.blockingIssues.isNotEmpty()) appendLine("Blocking: ${r.dependencies.blockingIssues.joinToString { "${it.key}: ${it.summary}" }}")
        if (r.dependencies.relatedIssues.isNotEmpty()) appendLine("Related: ${r.dependencies.relatedIssues.joinToString { "${it.key}: ${it.summary}" }}")
        if (r.acceptanceCriteria.isNotEmpty()) appendLine("AC: ${r.acceptanceCriteria.joinToString("; ") { it.description }}")
        if (r.similarTicketRefs.isNotEmpty()) appendLine("Similar: ${r.similarTicketRefs.joinToString()}")
    }

    companion object {
        private const val MAX_LINKED = 500
        private const val MAX_ATTACHMENT_TICKETS = 10
        private val TICKET_PATTERN = Regex("""[A-Z][A-Z0-9]+-\d+""")

        internal fun parseLinkedIds(record: KBRecord?, kbSearch: String, mainId: String): List<String> {
            val ids = mutableSetOf<String>()
            if (record != null) {
                // Dependencies — primary source of linked tickets
                record.dependencies.blockingIssues.forEach { ids.add(it.key) }
                record.dependencies.relatedIssues.forEach { ids.add(it.key) }
                // Similar ticket refs
                ids.addAll(record.similarTicketRefs)
                // Scan all text fields for ticket IDs
                val textFields = listOf(
                    record.requirementSummary, record.rationale,
                    record.businessSummary, record.asIsState, record.toBeState
                ) + record.extractedRequirements +
                    record.evolutionHistory.map { it.description }
                textFields.forEach { TICKET_PATTERN.findAll(it).forEach { m -> ids.add(m.value) } }
            }
            TICKET_PATTERN.findAll(kbSearch).forEach { ids.add(it.value) }
            ids.remove(mainId)
            ids.removeAll { it.isBlank() }
            return ids.toList()
        }
    }
}
