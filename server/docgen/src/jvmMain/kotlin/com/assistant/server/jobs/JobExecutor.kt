package com.assistant.server.jobs

import com.assistant.document.DocumentAggregator
import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.JobRepository
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory

/**
 * Executes a single generation job via subprocess orchestrator.
 * Subprocess-only — no legacy fallback, no feature flags.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 11.2
 */
open class JobExecutor(
    private val aggregator: DocumentAggregator,
    private val documentRepository: DocumentRepository,
    private val jobRepository: JobRepository,
    private val settingsRepository: SettingsRepository? = null,
    private val subprocessOrchestrator: BASubprocessOrchestrator? = null,
    private val kbRepository: com.assistant.kb.KBRepository? = null,
    private val aiOrchestrator: com.assistant.ai.AIOrchestrator? = null
) {

    private val logger = LoggerFactory.getLogger(JobExecutor::class.java)
    private val docHelper = JobExecutorDocHelper(documentRepository)

    open suspend fun execute(jobId: String, ticketId: String, docType: String) {
        val scope = CoroutineScope(Dispatchers.Default)
        val tracker = DocGenProgressTracker(jobId, jobRepository, scope)
        try {
            tracker.markStarted()
            ensureTicketAnalyzed(ticketId, tracker)
            val result = executeSubprocess(ticketId, docType)
            handleSubprocessResult(tracker, jobId, ticketId, docType, result)
        } catch (e: Exception) {
            logger.error("Job {} failed: {}", jobId, e.message, e)
            tracker.updateProgress(100, "FAILED")
            jobRepository.updateStatus(jobId, "FAILED", 100, "FAILED", e.message)
        } finally {
            tracker.stopHeartbeat()
        }
    }

    /** Ensure ticket has KB data. If not analyzed → trigger analyze first. */
    private suspend fun ensureTicketAnalyzed(ticketId: String, tracker: DocGenProgressTracker) {
        val kb = kbRepository ?: return
        val existing = kb.findByTicketId(ticketId)
        if (existing != null) {
            logger.info("Ticket {} already analyzed in KB, skipping re-analyze", ticketId)
            return
        }
        val orchestrator = aiOrchestrator ?: run {
            logger.warn("Ticket {} not in KB but AIOrchestrator unavailable", ticketId)
            return
        }
        logger.info("Ticket {} not in KB — triggering auto-analyze before BRD generation", ticketId)
        tracker.updateProgress(3, "ANALYZING_TICKET")
        orchestrator.analyzeTicket(ticketId, forceReanalyze = false)
        logger.info("Auto-analyze complete for {}", ticketId)
    }

    private suspend fun executeSubprocess(ticketId: String, docType: String): String {
        val orchestrator = subprocessOrchestrator
            ?: throw SubprocessFailedException("Subprocess orchestrator not configured")
        val cliBackend = settingsRepository?.get("cli_backend")
            ?.takeIf { it.isNotBlank() && it in BATaskConfig.VALID_CLI_BACKENDS }
            ?: throw SubprocessFailedException(
                "No AI backend configured. Go to Settings and select an AI provider (ollama, gemini, copilot, or kiro)."
            )
        val config = BATaskConfig(
            rootTicketId = ticketId,
            docType = docType,
            cliBackend = cliBackend
        )
        val result = orchestrator.executeTask(config)
        if (result.status == BATaskStatus.FAILED) {
            val reason = result.document.take(200).ifBlank { "no details" }
            val detail = "status=${result.status}, toolCalls=${result.toolCallsExecuted}, " +
                "duration=${result.totalDurationMs}ms, reason=$reason"
            throw SubprocessFailedException("Subprocess failed: $detail")
        }
        logger.info(
            "Subprocess succeeded: status={}, toolCalls={}, duration={}ms",
            result.status, result.toolCallsExecuted, result.totalDurationMs
        )
        return result.document
    }

    private suspend fun handleSubprocessResult(
        tracker: DocGenProgressTracker,
        jobId: String, ticketId: String, docType: String, document: String
    ) {
        docHelper.logPromptToFile(jobId, ticketId, docType, document)
        tracker.updateProgress(85, "PARSING_RESPONSE")
        val markdown = docHelper.parseResponse(docType, document)
        docHelper.saveDocument(tracker, ticketId, docType, markdown, null, SubprocessAgentStub)
    }
}

/** Signals a subprocess execution failure — cleaner than IllegalStateException. */
class SubprocessFailedException(message: String) : RuntimeException(message)
