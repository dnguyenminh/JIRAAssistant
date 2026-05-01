package com.assistant.server.analysis

import com.assistant.ai.AIAgent
import com.assistant.ai.AnalysisResult
import com.assistant.ai.AnalysisSource
import com.assistant.ai.ComplexityAssessment
import com.assistant.ai.ProviderConfig
import com.assistant.ai.RequirementSummary
import com.assistant.ai.deepanalysis.DeepAnalysisResponseParser
import com.assistant.ai.deepanalysis.models.MapReduceInfo
import com.assistant.server.analysis.models.BatchInfo
import com.assistant.server.analysis.models.BatchSummary
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.analysis.models.MapReduceResult
import com.assistant.server.document.models.TicketGraph
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory

/**
 * Orchestrates the Map-Reduce analysis pipeline.
 *
 * Splits a TicketGraph into batches, sends each batch to AI (map phase),
 * then combines all BatchSummaries into a final AnalysisResult (reduce phase).
 *
 * Reuses AI agent infrastructure from AIOrchestratorImpl — no separate
 * AI connections. Provider failover applies to both map and reduce calls.
 *
 * Requirements: 1.4, 3.3, 4.2, 6.4, 7.1-7.6, 10.1-10.5
 */
class MapReduceOrchestrator(
    private val batchStrategy: BatchStrategy,
    private val batchPromptBuilder: BatchPromptBuilder,
    private val reducePromptBuilder: ReducePromptBuilder,
    private val responseParser: DeepAnalysisResponseParser,
    private val configProvider: () -> MapReduceConfig,
    private val aiAnalysisSemaphore: Semaphore,
    private val progressTracker: ProgressTracker? = null
) {

    private val logger = LoggerFactory.getLogger(MapReduceOrchestrator::class.java)

    /**
     * Run Map-Reduce analysis on a TicketGraph.
     *
     * @param ticketId Root ticket ID
     * @param graph Full TicketGraph from traversal
     * @param agentProvider Provides AI agents from AIOrchestratorImpl
     * @param providerProvider Provides active providers sorted by priority
     * @return MapReduceResult containing AnalysisResult + metadata
     */
    suspend fun analyze(
        ticketId: String,
        graph: TicketGraph,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): MapReduceResult {
        val config = configProvider().validated()
        logConfig(ticketId, graph, config)
        val startTime = System.currentTimeMillis()

        val batches = batchStrategy.partition(graph)
        if (batches.isEmpty()) return buildEmptyResult(ticketId, startTime)

        val mapStart = System.currentTimeMillis()
        val summaries = mapPhase(batches, graph, agentProvider, providerProvider)
        val mapTimeMs = System.currentTimeMillis() - mapStart

        val successful = summaries.filterNotNull()
        if (successful.isEmpty()) return buildFallbackResult(ticketId, graph, startTime, mapTimeMs, batches.size)

        val reduceStart = System.currentTimeMillis()
        val reduceResult = reducePhase(ticketId, successful, graph, agentProvider, providerProvider)
        val reduceTimeMs = System.currentTimeMillis() - reduceStart

        val totalTimeMs = System.currentTimeMillis() - startTime
        progressTracker?.onComplete(totalTimeMs)
        logCompletion(ticketId, batches.size, successful.size, mapTimeMs, reduceTimeMs, totalTimeMs)

        return buildResult(reduceResult, batches, successful, mapTimeMs, reduceTimeMs)
    }

    internal suspend fun mapPhase(
        batches: List<BatchInfo>,
        graph: TicketGraph,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): List<BatchSummary?> {
        val config = configProvider().validated()
        val executor = MapPhaseExecutor(batchPromptBuilder, aiAnalysisSemaphore, progressTracker)
        return executor.execute(batches, graph, config, agentProvider, providerProvider)
    }

    internal suspend fun reducePhase(
        ticketId: String,
        summaries: List<BatchSummary>,
        graph: TicketGraph,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): ReduceResult {
        val config = configProvider().validated()
        val executor = ReducePhaseExecutor(reducePromptBuilder, responseParser, aiAnalysisSemaphore, progressTracker)
        return executor.execute(ticketId, summaries, graph, config.reduceTimeoutMs, agentProvider, providerProvider)
    }

    private fun logConfig(ticketId: String, graph: TicketGraph, config: MapReduceConfig) {
        logger.info(
            "Map-Reduce config: batch_size={}, concurrency={}, threshold={}",
            config.maxBatchSize, config.maxConcurrentBatches, config.mapReduceThreshold
        )
        logger.info(
            "Map-Reduce analysis started for {}: {} tickets",
            ticketId, graph.nodes.size
        )
    }

    private fun logCompletion(
        ticketId: String, total: Int, successful: Int,
        mapMs: Long, reduceMs: Long, totalMs: Long
    ) {
        logger.info(
            "Map-Reduce analysis completed for {}: {} batches ({} success, {} failed), " +
                "map={}ms, reduce={}ms, total={}ms",
            ticketId, total, successful, total - successful, mapMs, reduceMs, totalMs
        )
    }

    private fun buildResult(
        reduceResult: ReduceResult,
        batches: List<BatchInfo>,
        successful: List<BatchSummary>,
        mapTimeMs: Long,
        reduceTimeMs: Long
    ): MapReduceResult {
        val info = MapReduceInfo(
            totalBatches = batches.size,
            successfulBatches = successful.size,
            failedBatches = batches.size - successful.size,
            totalTicketsAnalyzed = successful.sumOf { it.ticketIds.size },
            mapPhaseTimeMs = mapTimeMs,
            reducePhaseTimeMs = reduceTimeMs,
            reduceSkipped = reduceResult.reduceSkipped
        )
        return MapReduceResult(analysisResult = reduceResult.analysisResult, mapReduceInfo = info)
    }

    private fun buildEmptyResult(ticketId: String, startTime: Long): MapReduceResult {
        val result = AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "No batches created"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 0.0, description = "Empty graph"),
            source = AnalysisSource.FRESH_AI
        )
        return MapReduceResult(result, MapReduceInfo(reduceSkipped = true))
    }

    private fun buildFallbackResult(
        ticketId: String, graph: TicketGraph,
        startTime: Long, mapTimeMs: Long, totalBatches: Int
    ): MapReduceResult {
        logger.error("All map batches failed, falling back to single-prompt with root context")
        val rootNode = graph.nodes[graph.rootTicketId]
        val result = AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(
                unified = rootNode?.issue?.summary ?: "Analysis failed — all batches failed"
            ),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 0.0, description = "Fallback: all batches failed"),
            source = AnalysisSource.FRESH_AI
        )
        val info = MapReduceInfo(
            totalBatches = totalBatches, successfulBatches = 0,
            failedBatches = totalBatches, totalTicketsAnalyzed = 0,
            mapPhaseTimeMs = mapTimeMs, reducePhaseTimeMs = 0
        )
        return MapReduceResult(result, info)
    }
}
