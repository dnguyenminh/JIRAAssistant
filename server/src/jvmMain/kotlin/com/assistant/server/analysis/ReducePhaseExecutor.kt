package com.assistant.server.analysis

import com.assistant.ai.AIAgent
import com.assistant.ai.AIResult
import com.assistant.ai.AnalysisResult
import com.assistant.ai.ProviderConfig
import com.assistant.ai.deepanalysis.DeepAnalysisParseException
import com.assistant.ai.deepanalysis.DeepAnalysisResponseParser
import com.assistant.server.analysis.models.BatchSummary
import com.assistant.server.document.models.TicketGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Executes the Reduce phase: combines BatchSummaries into a final
 * AnalysisResult via AI, with retry and fallback logic.
 *
 * Requirements: 4.2, 4.3, 4.7
 */
internal class ReducePhaseExecutor(
    private val reducePromptBuilder: ReducePromptBuilder,
    private val responseParser: DeepAnalysisResponseParser,
    private val aiAnalysisSemaphore: Semaphore,
    private val progressTracker: ProgressTracker?
) {

    private val logger = LoggerFactory.getLogger(ReducePhaseExecutor::class.java)

    companion object {
        private const val MAX_RETRIES = 3
        private const val BASE_RETRY_DELAY_MS = 2000L
    }

    /**
     * Run reduce phase. If only 1 summary, skip reduce and parse directly.
     */
    suspend fun execute(
        ticketId: String,
        summaries: List<BatchSummary>,
        graph: TicketGraph,
        reduceTimeoutMs: Long,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): ReduceResult {
        if (summaries.size == 1) return skipReduce(ticketId, summaries.first(), graph)
        return runReduce(ticketId, summaries, graph, reduceTimeoutMs, agentProvider, providerProvider)
    }

    private fun skipReduce(
        ticketId: String,
        summary: BatchSummary,
        graph: TicketGraph
    ): ReduceResult {
        logger.info("Single batch — skipping reduce phase")
        val prompt = reducePromptBuilder.buildPrompt(
            graph.nodes[graph.rootTicketId]?.issue
                ?: com.assistant.ai.deepanalysis.models.StructuredTicketContent(),
            listOf(summary), graph.metadata, 1
        )
        return try {
            val result = responseParser.parse(ticketId, buildSingleBatchJson(summary))
            ReduceResult(result, reduceSkipped = true)
        } catch (e: DeepAnalysisParseException) {
            logger.warn("Single-batch parse failed, building minimal result")
            ReduceResult(buildFallbackResult(ticketId, summary), reduceSkipped = true)
        }
    }

    private suspend fun runReduce(
        ticketId: String,
        summaries: List<BatchSummary>,
        graph: TicketGraph,
        reduceTimeoutMs: Long,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): ReduceResult {
        progressTracker?.onReduceStart(summaries.size)
        val rootTicket = graph.nodes[graph.rootTicketId]?.issue
            ?: com.assistant.ai.deepanalysis.models.StructuredTicketContent()
        val prompt = reducePromptBuilder.buildPrompt(
            rootTicket, summaries, graph.metadata, summaries.size
        )
        val response = retryReduceCall(prompt, reduceTimeoutMs, agentProvider, providerProvider)
        if (response != null) {
            return parseReduceResponse(ticketId, response, summaries)
        }
        logger.error("Reduce phase failed, using batch-0 summary as fallback")
        return ReduceResult(buildFallbackResult(ticketId, summaries.first()))
    }

    private fun parseReduceResponse(
        ticketId: String,
        response: String,
        summaries: List<BatchSummary>
    ): ReduceResult {
        return try {
            val result = responseParser.parse(ticketId, response)
            progressTracker?.onReduceComplete()
            ReduceResult(result)
        } catch (e: DeepAnalysisParseException) {
            logger.warn("Reduce parse failed: {}", e.message)
            ReduceResult(buildFallbackResult(ticketId, summaries.first()))
        }
    }

    private suspend fun retryReduceCall(
        prompt: String,
        timeoutMs: Long,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): String? {
        repeat(MAX_RETRIES) { attempt ->
            if (attempt > 0) {
                val delayMs = BASE_RETRY_DELAY_MS * (1L shl (attempt - 1))
                logger.info("Reduce retry {} after {}ms", attempt, delayMs)
                delay(delayMs)
            }
            val result = withTimeoutOrNull(timeoutMs) {
                callAI(prompt, agentProvider, providerProvider)
            }
            if (result != null) return result
        }
        return null
    }

    private suspend fun callAI(
        prompt: String,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): String? {
        val providers = providerProvider()
        val agents = agentProvider()
        for (provider in providers) {
            val agent = agents[provider.providerId] ?: continue
            val result = aiAnalysisSemaphore.withPermit { agent.analyze(prompt) }
            if (result is AIResult.Success) return result.response
        }
        return null
    }

    private fun buildSingleBatchJson(summary: BatchSummary): String {
        return buildString {
            appendLine("{")
            appendLine("""  "requirementSummary": { "unified": "${escapeJson(summary.requirementsSummary)}" },""")
            appendLine("""  "complexity": { "scrumPoints": 0.0, "description": "${escapeJson(summary.technicalInsights)}" },""")
            appendLine("""  "evolution": [],""")
            appendLine("""  "analysisMetadata": { "extractionConfidence": "MEDIUM" }""")
            appendLine("}")
        }
    }

    private fun buildFallbackResult(
        ticketId: String,
        summary: BatchSummary
    ): AnalysisResult {
        return AnalysisResult(
            ticketId = ticketId,
            context = com.assistant.ai.RequirementSummary(
                unified = summary.requirementsSummary.ifBlank { "Partial analysis from batch ${summary.batchIndex}" }
            ),
            evolution = emptyList(),
            complexity = com.assistant.ai.ComplexityAssessment(
                scrumPoints = 0.0,
                description = summary.technicalInsights.ifBlank { "Reduce phase failed — partial result" }
            ),
            source = com.assistant.ai.AnalysisSource.FRESH_AI
        )
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}

/** Internal result wrapper for reduce phase output. */
internal data class ReduceResult(
    val analysisResult: AnalysisResult,
    val reduceSkipped: Boolean = false
)
