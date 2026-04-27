package com.assistant.ai

import com.assistant.kb.KBRecord
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

/**
 * Batch analysis extension for AIOrchestratorImpl.
 * Handles multi-ticket prompts with retry and single-ticket fallback.
 * Req: AC 37, AC 40, AC 41, AC 42, AC 46, AC 47
 */

/** Analyze multiple tickets in a single AI prompt. */
suspend fun AIOrchestratorImpl.analyzeTicketBatchImpl(
    tickets: List<Pair<String, String>>,
    forceReanalyze: Boolean
): Map<String, AnalysisResult> {
    if (tickets.isEmpty()) return emptyMap()
    if (tickets.size == 1) return singleTicketFallback(tickets, forceReanalyze)

    // Phase 1: Filter KB-cached tickets
    val (cached, toAnalyze) = partitionCached(tickets, forceReanalyze)
    if (toAnalyze.isEmpty()) return cached

    // Phase 2: Split by content limit and process each sub-batch
    val subBatches = BatchPromptBuilder.splitByContentLimit(toAnalyze)
    val batchResults = mutableMapOf<String, AnalysisResult>()
    batchResults.putAll(cached)

    for (batch in subBatches) {
        val results = processSingleBatch(batch, forceReanalyze)
        batchResults.putAll(results)
    }
    return batchResults
}

/** Process one sub-batch: prompt → AI → parse → retry → fallback. */
private suspend fun AIOrchestratorImpl.processSingleBatch(
    batch: List<Pair<String, String>>,
    forceReanalyze: Boolean
): Map<String, AnalysisResult> {
    val expectedIds = batch.map { it.first }
    val providers = getActiveProvidersByPriority()
    if (providers.isEmpty()) return singleTicketFallback(batch, forceReanalyze)

    // Try batch prompt with retry
    for (retry in 0..1) {
        val prompt = BatchPromptBuilder.buildBatchPrompt(batch, isRetry = retry > 0)
        val response = callFirstAvailableProvider(prompt, providers) ?: continue
        val parsed = BatchResponseParser.parseBatchResponse(response, expectedIds)
        if (parsed != null && parsed.size == expectedIds.size) {
            saveBatchToKB(parsed.values.toList(), forceReanalyze)
            return parsed
        }
        // Partial results — save what we got, fallback for missing
        if (parsed != null && parsed.isNotEmpty()) {
            saveBatchToKB(parsed.values.toList(), forceReanalyze)
            val missing = BatchResponseParser.findMissingTicketIds(parsed, expectedIds)
            val missingTickets = batch.filter { it.first in missing }
            val fallbackResults = singleTicketFallback(missingTickets, forceReanalyze)
            return parsed + fallbackResults
        }
    }
    // All retries failed — fallback to single-ticket for entire batch
    println("[AIOrchestrator] Batch parse failed after retry, falling back to single-ticket mode")
    return singleTicketFallback(batch, forceReanalyze)
}

/** Call the first available AI provider with the given prompt. */
private suspend fun AIOrchestratorImpl.callFirstAvailableProvider(
    prompt: String,
    providers: List<ProviderConfig>
): String? {
    for (provider in providers) {
        val agents = getAgentsForBatch()
        val agent = agents[provider.providerId] ?: continue
        val result = withTimeoutOrNull(AIOrchestratorImpl.PROVIDER_TIMEOUT_MS) {
            agent.analyze(prompt)
        }
        when (result) {
            is AIResult.Success -> return result.response
            is AIResult.Failure -> {
                println("[AIOrchestrator] Provider ${provider.name} batch failed: ${result.error}")
                continue
            }
            null -> continue
        }
    }
    return null
}

/** Partition tickets into KB-cached and to-analyze lists. */
private suspend fun AIOrchestratorImpl.partitionCached(
    tickets: List<Pair<String, String>>,
    forceReanalyze: Boolean
): Pair<Map<String, AnalysisResult>, List<Pair<String, String>>> {
    if (forceReanalyze) return emptyMap<String, AnalysisResult>() to tickets
    val cached = mutableMapOf<String, AnalysisResult>()
    val toAnalyze = mutableListOf<Pair<String, String>>()
    for (ticket in tickets) {
        val kb = kbRepositoryForBatch().findByTicketId(ticket.first)
        if (kb != null) {
            cached[ticket.first] = kb.toAnalysisResultForBatch()
        } else {
            toAnalyze.add(ticket)
        }
    }
    return cached to toAnalyze
}

/** Fallback: analyze each ticket individually. */
private suspend fun AIOrchestratorImpl.singleTicketFallback(
    tickets: List<Pair<String, String>>,
    forceReanalyze: Boolean
): Map<String, AnalysisResult> {
    return tickets.associate { (id, content) ->
        id to analyzeTicket(id, content, forceReanalyze)
    }
}

private suspend fun AIOrchestratorImpl.saveBatchToKB(
    results: List<AnalysisResult>,
    forceReanalyze: Boolean
) {
    val repo = kbRepositoryForBatch()
    for (result in results) {
        val record = result.toKBRecordForBatch()
        if (forceReanalyze) repo.overwrite(record) else repo.save(record)
    }
}

private fun KBRecord.toAnalysisResultForBatch(): AnalysisResult = AnalysisResult(
    ticketId = ticketId,
    context = RequirementSummary(unified = requirementSummary),
    evolution = evolutionHistory.map { e ->
        EvolutionEntry(e.version, e.date, e.description, e.changeType)
    },
    complexity = ComplexityAssessment(
        scrumPoints = scrumPoints, description = rationale,
        kbReferences = similarTicketRefs.map { KBReference(it, confidenceScore * 100) }
    ),
    source = AnalysisSource.KB_CACHE
)

private fun AnalysisResult.toKBRecordForBatch(): KBRecord = KBRecord(
    ticketId = ticketId,
    requirementSummary = context.unified,
    evolutionHistory = evolution.map { e ->
        com.assistant.kb.EvolutionEntry(e.version, e.date, e.description, e.changeType)
    },
    scrumPoints = complexity.scrumPoints,
    confidenceScore = 0.8,
    rationale = complexity.description,
    similarTicketRefs = complexity.kbReferences.map { it.ticketId },
    timestamp = Clock.System.now().toEpochMilliseconds().toString()
)
