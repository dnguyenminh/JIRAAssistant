package com.assistant.server.analysis

import com.assistant.ai.AIAgent
import com.assistant.ai.AIResult
import com.assistant.ai.ProviderConfig
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.BatchInfo
import com.assistant.server.analysis.models.BatchSummary
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.document.models.TicketGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Executes the Map phase: sends each batch to AI concurrently,
 * respects concurrency limits, handles retries and timeouts.
 *
 * Requirements: 3.3, 3.4, 7.1, 7.2, 7.6, 8.1-8.5, 10.5
 */
internal class MapPhaseExecutor(
    private val batchPromptBuilder: BatchPromptBuilder,
    private val aiAnalysisSemaphore: Semaphore,
    private val progressTracker: ProgressTracker?
) {

    private val logger = LoggerFactory.getLogger(MapPhaseExecutor::class.java)

    companion object {
        private const val BASE_RETRY_DELAY_MS = 2000L
        private const val DEFAULT_MAX_RETRIES = 2
        private const val BATCH_ZERO_MAX_RETRIES = 4
    }

    /**
     * Process all batches concurrently with concurrency limit.
     * Returns list of BatchSummary (null for failed batches).
     */
    suspend fun execute(
        batches: List<BatchInfo>,
        graph: TicketGraph,
        config: MapReduceConfig,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): List<BatchSummary?> {
        val batchSemaphore = Semaphore(config.maxConcurrentBatches)
        val rootTicket = extractRootTicket(graph)
        progressTracker?.onMapStart(batches.size)

        return coroutineScope {
            batches.map { batch ->
                async {
                    batchSemaphore.withPermit {
                        processBatch(batch, rootTicket, graph, config, agentProvider, providerProvider)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun processBatch(
        batch: BatchInfo,
        rootTicket: StructuredTicketContent,
        graph: TicketGraph,
        config: MapReduceConfig,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): BatchSummary? {
        val maxRetries = if (batch.batchIndex == 0) BATCH_ZERO_MAX_RETRIES else DEFAULT_MAX_RETRIES
        val prompt = batchPromptBuilder.buildPrompt(batch, rootTicket, graph.edges)

        val result = retryWithBackoff(batch.batchIndex, maxRetries, config.batchTimeoutMs) {
            callAI(prompt, agentProvider, providerProvider)
        }
        return handleBatchResult(result, batch)
    }

    private fun handleBatchResult(
        result: String?,
        batch: BatchInfo
    ): BatchSummary? {
        if (result == null) {
            progressTracker?.onBatchFailed(batch.batchIndex, batch.totalBatches, "all retries exhausted")
            return null
        }
        return try {
            val summary = BatchSummaryParser.parseAndValidate(result, batch.batchIndex, batch.ticketIds)
            progressTracker?.onBatchComplete(batch.batchIndex, batch.totalBatches, batch.tickets.size)
            summary
        } catch (e: Exception) {
            logger.warn("Batch {} parse failed: {}", batch.batchIndex, e.message)
            progressTracker?.onBatchFailed(batch.batchIndex, batch.totalBatches, "parse error: ${e.message}")
            null
        }
    }

    private suspend fun retryWithBackoff(
        batchIndex: Int,
        maxRetries: Int,
        timeoutMs: Long,
        action: suspend () -> String?
    ): String? {
        repeat(maxRetries) { attempt ->
            if (attempt > 0) {
                val delayMs = BASE_RETRY_DELAY_MS * (1L shl (attempt - 1))
                logger.info("Batch {} retry {} after {}ms", batchIndex, attempt, delayMs)
                delay(delayMs)
            }
            val result = withTimeoutOrNull(timeoutMs) { action() }
            if (result != null) return result
            logger.warn("Batch {} attempt {} failed (timeout or error)", batchIndex, attempt + 1)
        }
        logger.error("Batch {} failed after {} retries", batchIndex, maxRetries)
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
            logger.warn("AI call failed for provider {}: {}", provider.name, (result as? AIResult.Failure)?.error)
        }
        return null
    }

    private fun extractRootTicket(graph: TicketGraph): StructuredTicketContent {
        return graph.nodes[graph.rootTicketId]?.issue
            ?: StructuredTicketContent(summary = "Unknown root ticket")
    }
}
