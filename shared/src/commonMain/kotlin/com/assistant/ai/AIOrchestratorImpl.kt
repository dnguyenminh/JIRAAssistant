package com.assistant.ai

import com.assistant.ai.deepanalysis.DeepAnalysisParseException
import com.assistant.ai.deepanalysis.DeepAnalysisPromptBuilder
import com.assistant.ai.deepanalysis.DeepAnalysisResponseParser
import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.kb.KBRepository
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

/**
 * AI Orchestrator with KB-First strategy, failover, and Deep Analysis (Req 21.1, 21.6).
 * Deep analysis components are optional — falls back to legacy when not injected.
 */
class AIOrchestratorImpl(
    private val kbRepository: KBRepository,
    private val agents: Map<String, AIAgent>,
    private val agentProvider: (() -> Map<String, AIAgent>)? = null,
    private val providerConfigs: MutableList<ProviderConfig> = mutableListOf(),
    private val providerConfigProvider: (() -> List<ProviderConfig>)? = null,
    // Deep Analysis dependencies — optional for backward compatibility (Req 21.1)
    private val jiraContentExtractor: JiraContentExtractor? = null,
    private val deepPromptBuilder: DeepAnalysisPromptBuilder? = null,
    private val deepResponseParser: DeepAnalysisResponseParser? = null,
    // Map-Reduce delegation — optional, null = single-prompt only (Req 10.1)
    private val mapReduceAnalyzer: MapReduceAnalyzer? = null
) : AIOrchestrator {

    internal fun currentAgents(): Map<String, AIAgent> = agentProvider?.invoke() ?: agents
    private fun currentProviderConfigs(): List<ProviderConfig> =
        providerConfigProvider?.invoke() ?: providerConfigs

    internal fun kbRepositoryForBatch() = kbRepository
    internal fun getAgentsForBatch() = currentAgents()

    companion object {
        const val PROVIDER_TIMEOUT_MS = 120_000L
        const val MAX_RETRIES = 2
    }

    private val failoverLog = mutableListOf<FailoverEvent>()

    data class FailoverEvent(
        val fromProvider: String, val toProvider: String,
        val reason: String, val timestamp: Long
    )

    /** Sealed result from tryMapReduceAnalysis — preserves extracted content on fall-through. */
    private sealed class MapReduceCheckResult {
        data class Analyzed(val result: AnalysisResult) : MapReduceCheckResult()
        data class FallThrough(val extractedContent: StructuredTicketContent?) : MapReduceCheckResult()
    }

    /** Check if deep analysis components are available. */
    private fun hasDeepAnalysis(): Boolean =
        jiraContentExtractor != null && deepPromptBuilder != null && deepResponseParser != null

    override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean): AnalysisResult {
        return analyzeTicket(ticketId, "", forceReanalyze)
    }

    override suspend fun analyzeTicketBatch(
        tickets: List<Pair<String, String>>,
        forceReanalyze: Boolean
    ): Map<String, AnalysisResult> = analyzeTicketBatchImpl(tickets, forceReanalyze)

    /**
     * Main analysis entry point. Upgraded to use Deep Analysis pipeline (Req 21.1).
     * KB-First strategy, provider failover, retry logic preserved (Req 21.6).
     * Delegates to Map-Reduce when linked tickets exceed threshold (Req 10.2).
     */
    override suspend fun analyzeTicket(
        ticketId: String, ticketContent: String, forceReanalyze: Boolean
    ): AnalysisResult {
        if (!forceReanalyze) {
            val cached = kbRepository.findByTicketId(ticketId)
            if (cached != null && !isErrorRecord(cached)) {
                return cached.toAnalysisResult(AnalysisSource.KB_CACHE)
            }
        }
        val activeProviders = getActiveProvidersByPriority()
        if (activeProviders.isEmpty()) return errorResult(ticketId, "All AI providers are offline")

        val checkResult = tryMapReduceAnalysis(ticketId, forceReanalyze)
        when (checkResult) {
            is MapReduceCheckResult.Analyzed -> return checkResult.result
            is MapReduceCheckResult.FallThrough -> {
                return tryProvidersWithFailover(
                    ticketId, ticketContent, activeProviders, forceReanalyze,
                    checkResult.extractedContent
                )
            }
        }
    }

    /**
     * Check if map-reduce should be used and delegate if so (Req 6.1-6.6, 10.2).
     * Returns [MapReduceCheckResult] to preserve extracted content on fall-through.
     */
    private suspend fun tryMapReduceAnalysis(
        ticketId: String, forceReanalyze: Boolean
    ): MapReduceCheckResult {
        val analyzer = mapReduceAnalyzer ?: return MapReduceCheckResult.FallThrough(null)
        if (!analyzer.isEnabled) return MapReduceCheckResult.FallThrough(null)
        if (!hasDeepAnalysis()) return MapReduceCheckResult.FallThrough(null)

        val content = try {
            jiraContentExtractor!!.extract(ticketId)
        } catch (e: Exception) {
            println("[AIOrchestratorImpl] Content extraction failed for map-reduce check: ${e.message}")
            return MapReduceCheckResult.FallThrough(null)
        }

        val linkedCount = content.linkedTicketContents.size
        if (linkedCount <= analyzer.threshold) return MapReduceCheckResult.FallThrough(content)

        return try {
            val result = analyzer.analyzeWithMapReduce(
                ticketId, content,
                { currentAgents() },
                { getActiveProvidersByPriority() }
            )
            saveToKB(result, forceReanalyze)
            MapReduceCheckResult.Analyzed(result)
        } catch (e: Exception) {
            println("[AIOrchestratorImpl] Map-reduce analysis failed for $ticketId, falling back to single-prompt: ${e.message}")
            MapReduceCheckResult.FallThrough(content)
        }
    }

    /** Check if KB record contains an error result (empty analysis). */
    private fun isErrorRecord(record: com.assistant.kb.KBRecord): Boolean {
        val summary = record.requirementSummary
        return summary.startsWith("Error:") || summary.isBlank()
    }

    private suspend fun tryProvidersWithFailover(
        ticketId: String, ticketContent: String,
        providers: List<ProviderConfig>, forceReanalyze: Boolean,
        preExtractedContent: StructuredTicketContent? = null
    ): AnalysisResult {
        var lastError: String? = null
        for (provider in providers) {
            val agent = currentAgents()[provider.providerId] ?: continue
            val result = tryAnalyzeWithRetry(ticketId, ticketContent, agent, provider, preExtractedContent)
            if (result != null) {
                saveToKB(result, forceReanalyze)
                return result
            }
            lastError = "Provider ${provider.name} failed or timed out"
            logFailover(provider, providers)
        }
        return errorResult(ticketId, lastError ?: "Analysis failed")
    }

    private suspend fun saveToKB(result: AnalysisResult, forceReanalyze: Boolean) {
        val kbRecord = result.toKBRecord()
        if (forceReanalyze) kbRepository.overwrite(kbRecord) else kbRepository.save(kbRecord)
    }

    private fun logFailover(current: ProviderConfig, all: List<ProviderConfig>) {
        val next = all.getOrNull(all.indexOf(current) + 1) ?: return
        failoverLog.add(FailoverEvent(current.providerId, next.providerId, "timeout_or_failure", currentTimeMillis()))
    }

    /**
     * Try analysis with retry. Uses Deep Analysis pipeline when available (Req 21.1).
     * Falls back to legacy prompt/parse when deep analysis components not injected.
     */
    private suspend fun tryAnalyzeWithRetry(
        ticketId: String, ticketContent: String,
        agent: AIAgent, provider: ProviderConfig,
        preExtractedContent: StructuredTicketContent? = null
    ): AnalysisResult? {
        val prompt = try {
            buildPromptForAnalysis(ticketId, ticketContent, preExtractedContent)
        } catch (e: Exception) {
            println("[AIOrchestratorImpl] Prompt build failed for $ticketId on ${provider.name}: ${e.message}")
            e.printStackTrace()
            return null
        }
        return retryWithPrompt(ticketId, prompt, agent)
    }

    private suspend fun retryWithPrompt(
        ticketId: String, basePrompt: String, agent: AIAgent
    ): AnalysisResult? {
        var retries = 0
        while (retries <= MAX_RETRIES) {
            val prompt = if (retries > 0) appendRetryHint(basePrompt) else basePrompt
            val parsed = callAndParse(ticketId, prompt, agent)
            if (parsed != null) return parsed
            retries++
        }
        return null
    }

    private suspend fun callAndParse(
        ticketId: String, prompt: String, agent: AIAgent
    ): AnalysisResult? {
        val aiResult = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { agent.analyze(prompt) } ?: return null
        return when (aiResult) {
            is AIResult.Success -> parseResponse(ticketId, aiResult.response)
            is AIResult.Failure -> null
        }
    }

    /** Build prompt: deep analysis pipeline or legacy fallback. */
    private suspend fun buildPromptForAnalysis(
        ticketId: String, ticketContent: String,
        preExtractedContent: StructuredTicketContent? = null
    ): String {
        if (!hasDeepAnalysis()) return buildLegacyPrompt(ticketId, ticketContent)
        return buildDeepAnalysisPrompt(ticketId, preExtractedContent)
    }

    /** Req 21.1 — Extract structured content, build deep prompt. Reuses pre-extracted content when available. */
    private suspend fun buildDeepAnalysisPrompt(
        ticketId: String,
        preExtractedContent: StructuredTicketContent? = null
    ): String {
        val content = preExtractedContent ?: jiraContentExtractor!!.extract(ticketId)
        return deepPromptBuilder!!.buildPrompt(content)
    }

    /** Parse response: deep analysis parser or legacy fallback. */
    private fun parseResponse(ticketId: String, response: String): AnalysisResult? {
        if (!hasDeepAnalysis()) return parseLegacyResponse(ticketId, response)
        return parseDeepAnalysisResponse(ticketId, response)
    }

    /** Req 21.1 — Parse via DeepAnalysisResponseParser. */
    private fun parseDeepAnalysisResponse(ticketId: String, response: String): AnalysisResult? {
        return try {
            deepResponseParser!!.parse(ticketId, response)
        } catch (_: DeepAnalysisParseException) {
            null
        }
    }

    private fun appendRetryHint(prompt: String): String {
        return "$prompt\nIMPORTANT: Return ONLY valid JSON. No markdown, no extra text."
    }

    /** Legacy prompt builder — used when deep analysis components not available. */
    private fun buildLegacyPrompt(ticketId: String, ticketContent: String): String =
        LegacyPromptBuilder.build(ticketId, ticketContent)

    /** Legacy response parser — used when deep analysis components not available. */
    internal fun parseLegacyResponse(ticketId: String, response: String): AnalysisResult? =
        LegacyResponseMapper.parseResponse(ticketId, response)

    override suspend fun testProvider(providerId: String): ProviderTestResult {
        val agent = currentAgents()[providerId]
            ?: return ProviderTestResult(providerId, false, 0, "Provider not found")
        return ProviderTester.test(agent, providerId)
    }

    override suspend fun getProviderStatuses(): List<ProviderStatus> {
        return currentProviderConfigs().map { config ->
            ProviderStatus(config.providerId, config.name, config.status)
        }
    }

    override fun setFailoverOrder(providerIds: List<String>) {
        val configs = currentProviderConfigs().toMutableList()
        providerIds.forEachIndexed { idx, id ->
            configs.find { it.providerId == id }?.let { c ->
                configs[configs.indexOf(c)] = c.copy(priority = idx)
            }
        }
        configs.sortBy { it.priority }
        providerConfigs.clear()
        providerConfigs.addAll(configs)
    }

    internal fun getActiveProvidersByPriority(): List<ProviderConfig> {
        return currentProviderConfigs()
            .filter { it.status == ConnectionStatus.ACTIVE }
            .sortedBy { it.priority }
    }

    fun getFailoverLog(): List<FailoverEvent> = failoverLog.toList()

    override fun analyzeBottlenecks(
        totalTickets: Int, resolvedCount: Int,
        cycleTimeDays: Double, blockedCount: Int
    ) = analyzeBottlenecksImpl(totalTickets, resolvedCount, cycleTimeDays, blockedCount)

    override fun generateVelocityTrend(totalTickets: Int, resolvedCount: Int) =
        generateVelocityTrendImpl(totalTickets, resolvedCount)

    override fun calculateAIVelocity(totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double) =
        calculateAIVelocityImpl(totalTickets, resolvedCount, cycleTimeDays)

    private fun errorResult(ticketId: String, message: String): AnalysisResult {
        return AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "Error: $message"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 0.0, description = message),
            source = AnalysisSource.FRESH_AI
        )
    }
}

internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
