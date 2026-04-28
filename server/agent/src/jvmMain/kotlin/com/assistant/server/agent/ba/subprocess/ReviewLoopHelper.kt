package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.server.agent.subprocess.MessageProtocol
import org.slf4j.LoggerFactory

/**
 * Multi-turn review loop for subprocess-generated documents.
 *
 * After the initial tool call loop completes, checks document quality.
 * If quality is insufficient, sends review feedback to the SAME subprocess
 * for revision. Repeats up to [MAX_REVIEW_ITERATIONS] times.
 *
 * Requirements: 1.1 (multi-turn reasoning)
 */
class ReviewLoopHelper(
    private val subprocessManager: SubprocessManager,
    private val subprocessProxy: SubprocessProxy,
    private val progressReporter: ProgressReporter
) {

    private val logger = LoggerFactory.getLogger(ReviewLoopHelper::class.java)

    companion object {
        private const val AGENT_TYPE = "ba-agent"
        const val MAX_REVIEW_ITERATIONS = 2
    }

    /**
     * Runs the review loop on an initial [ToolCallLoopResult].
     * Returns the final (possibly revised) result.
     */
    suspend fun reviewLoop(
        initial: ToolCallLoopResult, config: BATaskConfig
    ): ToolCallLoopResult {
        var result = initial
        var iteration = 0
        while (iteration < MAX_REVIEW_ITERATIONS) {
            val quality = DocumentQualityChecker.check(result.document, config.docType)
            if (quality.passed) {
                logger.info("Document quality passed on iteration {}", iteration)
                break
            }
            logQualityFailure(quality, iteration)
            result = sendReviewAndRunLoop(quality.feedback, config, result)
            iteration++
        }
        return result
    }

    private suspend fun sendReviewAndRunLoop(
        feedback: String, config: BATaskConfig, prev: ToolCallLoopResult
    ): ToolCallLoopResult {
        val reviewMsg = MessageProtocol.formatCommand(feedback)
        val stdoutFlow = subprocessManager.sendCommand(AGENT_TYPE, reviewMsg)
        val engine = ToolCallLoopEngine(subprocessProxy, progressReporter)
        val writer: suspend (String) -> Unit = { msg ->
            subprocessManager.sendCommand(AGENT_TYPE, msg)
        }
        val next = engine.runLoop(
            stdoutFlow, writer, config.maxToolCalls, config.taskTimeoutSeconds
        )
        return mergeResults(prev, next)
    }

    private fun mergeResults(
        prev: ToolCallLoopResult, next: ToolCallLoopResult
    ) = ToolCallLoopResult(
        document = next.document,
        toolCallsExecuted = prev.toolCallsExecuted + next.toolCallsExecuted,
        toolCallsFailed = prev.toolCallsFailed + next.toolCallsFailed,
        toolCallLog = prev.toolCallLog + next.toolCallLog,
        timedOut = next.timedOut
    )

    private fun logQualityFailure(
        quality: DocumentQualityChecker.QualityResult, iteration: Int
    ) {
        logger.info(
            "Quality check failed (iteration {}): missing={}, weak={}",
            iteration, quality.missingSections.size, quality.weakSections.size
        )
    }
}
