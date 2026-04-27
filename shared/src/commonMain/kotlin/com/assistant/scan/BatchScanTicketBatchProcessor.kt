package com.assistant.scan

import com.assistant.ai.AnalysisResult
import com.assistant.ai.AnalysisSource
import com.assistant.kb.KBRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

/**
 * Batch prompt processing for BatchScanEngine.
 * Groups tickets into batches, sends single AI prompt per batch.
 * Req: AC 43, AC 44, AC 45
 */

private const val DEFAULT_BATCH_PROMPT_SIZE = 3
private const val BATCH_PROMPT_SIZE_KEY = "batch_prompt_size"

/** Read batch_prompt_size from settings, default 3, validate >= 1. Req: AC 34 */
internal suspend fun BatchScanEngine.getBatchPromptSize(): Int {
    val raw = settingsRepository?.get(BATCH_PROMPT_SIZE_KEY)
    val value = raw?.toIntOrNull() ?: DEFAULT_BATCH_PROMPT_SIZE
    return if (value >= 1) value else DEFAULT_BATCH_PROMPT_SIZE
}

/**
 * Process a batch of tickets using batch prompt optimization.
 * Fetches content for all tickets, then sends single AI call.
 * Falls back to single-ticket mode if batch size is 1.
 */
internal suspend fun BatchScanEngine.processBatchPrompt(
    projectKey: String,
    ticketIds: List<String>
) {
    val batchSize = getBatchPromptSize()
    if (batchSize <= 1) {
        processBatchSingleMode(projectKey, ticketIds)
        return
    }
    processBatchMultiMode(projectKey, ticketIds, batchSize)
}

/** Single-ticket mode: process each ticket individually (backward compat). */
private suspend fun BatchScanEngine.processBatchSingleMode(
    projectKey: String,
    ticketIds: List<String>
) {
    for (ticketId in ticketIds) {
        processTicket(projectKey, ticketId)
    }
}

/** Multi-ticket mode: group tickets and send batch prompts. */
private suspend fun BatchScanEngine.processBatchMultiMode(
    projectKey: String,
    ticketIds: List<String>,
    batchSize: Int
) {
    val batches = ticketIds.chunked(batchSize)
    for (batch in batches) {
        processSingleBatchPrompt(projectKey, batch)
    }
}

/** Process one batch: fetch content → AI batch call → save results → attachments → log. */
private suspend fun BatchScanEngine.processSingleBatchPrompt(
    projectKey: String,
    ticketIds: List<String>
) {
    logToBoth(projectKey, ticketIds.first(), ScanLogStatus.ANALYZING,
        "Batch analyzing ${ticketIds.size} tickets: ${ticketIds.joinToString(", ")}")
    try {
        val ticketPairs = fetchBatchContent(ticketIds)
        val results = aiSemaphore.withPermit {
            aiOrchestrator.analyzeTicketBatch(ticketPairs, forceReanalyze)
        }
        val placeholderIds = saveBatchResults(projectKey, results)
        logBatchCompletion(projectKey, ticketIds, results)
        // Fallback: re-analyze placeholder tickets via single-ticket mode
        for (ticketId in placeholderIds) {
            processTicket(projectKey, ticketId)
        }
        // Process attachments for each ticket in batch
        processBatchAttachments(projectKey, ticketIds)
    } catch (e: Exception) {
        logToBoth(projectKey, ticketIds.first(), ScanLogStatus.FAILED,
            "Batch analysis failed: ${e.message ?: "Unknown error"}")
    }
}

/** Fetch content for all tickets in batch — parallel for I/O efficiency. */
private suspend fun BatchScanEngine.fetchBatchContent(
    ticketIds: List<String>
): List<Pair<String, String>> {
    return kotlinx.coroutines.coroutineScope {
        ticketIds.map { ticketId ->
            async {
                ticketId to fetchTicketContentForBatch(ticketId)
            }
        }.map { it.await() }
    }
}

/**
 * Fetch single ticket content for batch mode.
 * Uses jiraContentExtractor when available for rich content extraction.
 * Falls back to legacy Jira fetch when extractor is not injected or throws.
 */
private suspend fun BatchScanEngine.fetchTicketContentForBatch(ticketId: String): String {
    if (jiraContentExtractor != null) {
        return try {
            val content = jiraContentExtractor.extract(ticketId)
            formatStructuredContent(content)
        } catch (e: Exception) {
            println("[BatchScanEngine] Content extraction failed for $ticketId: ${e.message}")
            fetchLegacyBatchContent(ticketId)
        }
    }
    return fetchLegacyBatchContent(ticketId)
}

/** Legacy fetch: summary + description from Jira API for batch mode. */
private suspend fun BatchScanEngine.fetchLegacyBatchContent(ticketId: String): String {
    return try {
        val issue = jiraClientProvider().getIssueDetails(ticketId) ?: return ""
        buildString {
            appendLine("Summary: ${issue.fields.summary}")
            val desc = issue.fields.descriptionText
            if (desc.isNotBlank()) appendLine("Description: $desc")
        }.take(3000)
    } catch (_: Exception) { "" }
}

/** Save batch results to KB, skipping placeholders. Returns placeholder ticket IDs for fallback. */
private suspend fun BatchScanEngine.saveBatchResults(
    projectKey: String,
    results: Map<String, AnalysisResult>
): List<String> {
    val placeholderTicketIds = mutableListOf<String>()
    for ((_, result) in results) {
        if (isPlaceholderResult(result)) {
            placeholderTicketIds.add(result.ticketId)
            continue
        }
        saveValidResult(projectKey, result)
    }
    return placeholderTicketIds
}

private suspend fun BatchScanEngine.saveValidResult(
    projectKey: String,
    result: AnalysisResult
) {
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
    invokeKBHook(projectKey, record)
}

private fun BatchScanEngine.invokeKBHook(projectKey: String, record: KBRecord) {
    val hook = onKBRecordSaved ?: return
    CoroutineScope(Dispatchers.Default).launch {
        try { hook(projectKey, record) } catch (_: Exception) {}
    }
}

/** Log batch completion with ticket IDs and source info. Req: AC 45 */
private suspend fun BatchScanEngine.logBatchCompletion(
    projectKey: String,
    ticketIds: List<String>,
    results: Map<String, AnalysisResult>
) {
    val source = determineBatchSource(results)
    val ids = ticketIds.joinToString(", ")
    logToBoth(projectKey, ticketIds.first(), ScanLogStatus.COMPLETED,
        "Batch analyzed: $ids (${ticketIds.size} tickets in 1 prompt, source: $source)")
}

/** Determine batch source: FRESH_AI if all fresh, MIXED if some cached. */
private fun determineBatchSource(results: Map<String, AnalysisResult>): String {
    val sources = results.values.map { it.source }.toSet()
    return when {
        sources.size == 1 && sources.first() == AnalysisSource.FRESH_AI -> "FRESH_AI"
        sources.size == 1 && sources.first() == AnalysisSource.KB_CACHE -> "KB_CACHE"
        else -> "MIXED"
    }
}
