package com.assistant.scan

import com.assistant.ai.AnalysisResult
import com.assistant.jira.JiraIssue
import com.assistant.kb.KBRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

/**
 * Ticket processing logic extracted from BatchScanEngine.
 * Handles AI analysis, KB save, relationship logging, and attachment processing.
 */

/** Process a single ticket: fetch content (parallel-safe) → AI analysis (semaphore) → KB save → attachments. */
internal suspend fun BatchScanEngine.processTicket(projectKey: String, ticketId: String) {
    logToBoth(projectKey, ticketId, ScanLogStatus.ANALYZING, "Analyzing ticket $ticketId")
    try {
        // Phase 1: Jira fetch — runs in parallel (no semaphore)
        val content = fetchTicketContent(ticketId)
        // Phase 2: AI analysis — semaphore-limited (Ollama handles 1 at a time)
        val result = aiSemaphore.withPermit {
            aiOrchestrator.analyzeTicket(ticketId, content, forceReanalyze = forceReanalyze)
        }
        saveKBRecord(projectKey, result)
        logToBoth(projectKey, ticketId, ScanLogStatus.COMPLETED, "AI analysis completed (source: ${result.source})")
        // Phase 3: Relationships + attachments — parallel-safe
        logIssueRelationships(projectKey, ticketId)
        processAttachmentsIfAvailable(projectKey, ticketId)
    } catch (e: Exception) {
        println("[BatchScanEngine] processTicket failed for $ticketId in $projectKey: ${e.message}")
        logToBoth(projectKey, ticketId, ScanLogStatus.FAILED, "Analysis failed: ${e.message ?: "Unknown error"}")
    }
}

/** Fetch ticket summary + description from Jira for enriched AI prompt. */
private suspend fun BatchScanEngine.fetchTicketContent(ticketId: String): String {
    return try {
        val issue = jiraClientProvider().getIssueDetails(ticketId) ?: return ""
        buildString {
            appendLine("Summary: ${issue.fields.summary}")
            val desc = issue.fields.descriptionText
            if (desc.isNotBlank()) appendLine("Description: $desc")
            val status = issue.fields.status?.name
            if (!status.isNullOrBlank()) appendLine("Status: $status")
            val resolution = issue.fields.resolution?.name
            if (!resolution.isNullOrBlank()) appendLine("Resolution: $resolution")
        }.take(3000) // cap to avoid prompt overflow
    } catch (e: Exception) {
        println("[BatchScanEngine] Failed to fetch content for $ticketId: ${e.message}")
        ""
    }
}

private suspend fun BatchScanEngine.saveKBRecord(projectKey: String, result: AnalysisResult) {
    val record = KBRecord(
        ticketId = result.ticketId,
        requirementSummary = result.context.unified,
        evolutionHistory = result.evolution.map { e ->
            com.assistant.kb.EvolutionEntry(e.version, e.date, e.description, e.changeType)
        },
        scrumPoints = result.complexity.scrumPoints,
        confidenceScore = 0.8,
        rationale = result.complexity.description,
        similarTicketRefs = result.complexity.kbReferences.map { it.ticketId },
        timestamp = Clock.System.now().toEpochMilliseconds().toString()
    )
    kbRepository.save(record)
    invokeKBRecordSavedHook(projectKey, record)
}

/** Fire-and-forget hook after KBRecord save. Req: 14.1, 16.1 */
private fun BatchScanEngine.invokeKBRecordSavedHook(projectKey: String, record: KBRecord) {
    val hook = onKBRecordSaved ?: return
    CoroutineScope(Dispatchers.Default).launch {
        try { hook(projectKey, record) }
        catch (e: Exception) { println("[BatchScanEngine] onKBRecordSaved hook failed: ${e.message}") }
    }
}

private suspend fun BatchScanEngine.logIssueRelationships(projectKey: String, ticketId: String) {
    try {
        val issue = jiraClientProvider().getIssueDetails(ticketId) ?: return
        logIssueLinks(projectKey, ticketId, issue)
        logParentSubtasks(projectKey, ticketId, issue)
    } catch (e: Exception) {
        println("[BatchScanEngine] Failed to log relationships for $ticketId: ${e.message}")
    }
}

private suspend fun BatchScanEngine.logIssueLinks(projectKey: String, ticketId: String, issue: JiraIssue) {
    val links = issue.fields.issuelinks ?: emptyList()
    if (links.isEmpty()) return
    val typeCounts = links.groupBy { it.type?.name ?: "Unknown" }
        .map { "${it.key}(${it.value.size})" }.joinToString(", ")
    logToBoth(projectKey, ticketId, ScanLogStatus.COMPLETED, "Found ${links.size} issue links for $ticketId: $typeCounts")
}

private suspend fun BatchScanEngine.logParentSubtasks(projectKey: String, ticketId: String, issue: JiraIssue) {
    val parent = issue.fields.parent
    val subtasks = issue.fields.subtasks ?: emptyList()
    if (parent != null && parent.key.isNotBlank()) {
        logToBoth(projectKey, ticketId, ScanLogStatus.COMPLETED, "Parent: ${parent.key}")
    }
    if (subtasks.isNotEmpty()) {
        val keys = subtasks.joinToString(", ") { it.key }
        logToBoth(projectKey, ticketId, ScanLogStatus.COMPLETED, "Subtasks: ${subtasks.size} ($keys)")
    }
}

private suspend fun BatchScanEngine.processAttachmentsIfAvailable(projectKey: String, ticketId: String) {
    val processor = attachmentProcessor ?: return
    try {
        val issue = jiraClientProvider().getIssueDetails(ticketId) ?: return
        val attachments = issue.fields.attachment ?: emptyList()
        if (attachments.isEmpty()) return
        val chunks = processor(projectKey, ticketId, attachments)
        logToBoth(projectKey, ticketId, ScanLogStatus.COMPLETED,
            "Processed ${attachments.size} attachments for $ticketId ($chunks chunks)")
    } catch (e: Exception) {
        println("[BatchScanEngine] Attachment processing failed for $ticketId: ${e.message}")
    }
}

/** Build the relationship network graph and save to KB. */
internal suspend fun BatchScanEngine.buildAndSaveGraph(projectKey: String) {
    try {
        val issues = jiraClientProvider().getIssues(projectKey, maxResults = Int.MAX_VALUE)
        if (issues.isEmpty()) return
        val graph = featureNetworkMapper.map(issues)
        kbRepository.saveGraphData(projectKey, graph)
        val linkEdges = graph.edges.count { it.relationshipType.startsWith("link") }
        val parentEdges = graph.edges.count { it.relationshipType.startsWith("parent") }
        val keywordEdges = graph.edges.count { it.relationshipType.startsWith("keyword") }
        val withDesc = graph.nodes.count { !it.description.isNullOrBlank() }
        val types = graph.nodes.groupBy { it.featureName ?: "Unknown" }.map { "${it.key}(${it.value.size})" }.joinToString(", ")
        logToBoth(projectKey, "-", ScanLogStatus.COMPLETED,
            "Graph built: ${graph.nodes.size} nodes ($withDesc with description), ${graph.edges.size} edges (links: $linkEdges, parent: $parentEdges, keyword: $keywordEdges), types: $types")
    } catch (e: Exception) {
        println("[BatchScanEngine] Failed to build graph for $projectKey: ${e.message}")
    }
}
