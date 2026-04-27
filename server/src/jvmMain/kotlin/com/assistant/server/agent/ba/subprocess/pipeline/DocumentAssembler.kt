package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext
import com.assistant.server.agent.ba.subprocess.pipeline.models.StepResponse
import org.slf4j.LoggerFactory

/**
 * Assembles pipeline step responses and collected context
 * into a final [BATaskResult] with backward-compatible metrics.
 *
 * Requirements: 6.1, 6.3, 6.4, 7.2, 7.3, 7.4
 */
class DocumentAssembler {

    private val logger = LoggerFactory.getLogger(DocumentAssembler::class.java)

    fun assemble(
        stepResponses: List<StepResponse>,
        collectedContext: CollectedContext,
        startTimeMs: Long
    ): BATaskResult {
        val lastNonEmpty = stepResponses.lastOrNull {
            it.content.isNotBlank()
        }
        val totalDurationMs = System.currentTimeMillis() - startTimeMs
        if (lastNonEmpty == null) {
            return buildResult("", collectedContext, totalDurationMs, BATaskStatus.FAILED)
        }
        val status = determineStatus(stepResponses, collectedContext)
        logSummary(lastNonEmpty.content, stepResponses.size, totalDurationMs)
        return buildResult(lastNonEmpty.content, collectedContext, totalDurationMs, status)
    }

    private fun determineStatus(
        responses: List<StepResponse>,
        context: CollectedContext
    ): BATaskStatus {
        val anyTimeout = responses.any { it.timedOut }
        if (anyTimeout) return BATaskStatus.TIMEOUT
        val anyToolFailed = context.toolCallLog.any { !it.success }
        return if (anyToolFailed) BATaskStatus.PARTIAL else BATaskStatus.SUCCESS
    }

    private fun buildResult(
        document: String,
        context: CollectedContext,
        totalDurationMs: Long,
        status: BATaskStatus
    ): BATaskResult {
        val log = context.toolCallLog
        return BATaskResult(
            document = document,
            toolCallsExecuted = log.size,
            toolCallsFailed = log.count { !it.success },
            totalDurationMs = totalDurationMs,
            status = status,
            toolCallLog = log
        )
    }

    private fun logSummary(document: String, turns: Int, durationMs: Long) {
        logger.info(
            "Document assembled: {} chars, {} turns, {}ms",
            document.length, turns, durationMs
        )
    }
}
