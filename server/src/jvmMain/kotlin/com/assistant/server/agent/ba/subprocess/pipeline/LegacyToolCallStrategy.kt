package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.server.agent.ba.subprocess.ReviewLoopHelper
import com.assistant.server.agent.ba.subprocess.TaskMessageBuilder
import com.assistant.server.agent.ba.subprocess.ToolCallLoopEngine
import com.assistant.server.agent.ba.subprocess.ToolCallLoopResult
import org.slf4j.LoggerFactory

/**
 * Legacy strategy wrapping the existing single-shot tool call loop.
 *
 * Delegates to [ToolCallLoopEngine] + [ReviewLoopHelper] +
 * [TaskMessageBuilder] — the original flow extracted from
 * `BASubprocessOrchestrator.sendTaskWithReviewLoop()`.
 *
 * Requirements: 10.1, 10.3
 */
class LegacyToolCallStrategy(
    private val subprocessManager: SubprocessManager,
    private val subprocessProxy: SubprocessProxy,
    private val progressReporter: ProgressReporter
) : PipelineStrategy {

    private val logger = LoggerFactory.getLogger(LegacyToolCallStrategy::class.java)

    override suspend fun execute(
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ): BATaskResult {
        val startTime = System.currentTimeMillis()
        val loopResult = sendTaskWithReviewLoop(config)
        return buildResult(loopResult, startTime, config)
    }

    private suspend fun sendTaskWithReviewLoop(
        config: BATaskConfig
    ): ToolCallLoopResult {
        val initial = sendTaskAndRunLoop(config)
        val reviewer = ReviewLoopHelper(
            subprocessManager, subprocessProxy, progressReporter
        )
        return reviewer.reviewLoop(initial, config)
    }

    private suspend fun sendTaskAndRunLoop(
        config: BATaskConfig
    ): ToolCallLoopResult {
        injectToolList()
        val tools = subprocessProxy.getAvailableToolDescriptors()
        logger.info("Legacy tool list injected: {} tools", tools.size)
        progressReporter.reportProgress(10, "Task sent")
        val taskMsg = TaskMessageBuilder.buildTaskMessage(
            config, tools, isRealCli = true
        )
        val stdoutFlow = subprocessManager.sendCommand(AGENT_TYPE, taskMsg)
        val engine = ToolCallLoopEngine(subprocessProxy, progressReporter)
        val writer: suspend (String) -> Unit = { msg ->
            subprocessManager.sendCommand(AGENT_TYPE, msg)
        }
        return engine.runLoop(
            stdoutFlow, writer,
            config.maxToolCalls, config.taskTimeoutSeconds
        )
    }

    private suspend fun injectToolList() {
        val msg = subprocessProxy.buildToolListMessage()
        subprocessManager.sendCommand(AGENT_TYPE, msg)
    }

    private fun buildResult(
        loop: ToolCallLoopResult, startTime: Long, config: BATaskConfig
    ): BATaskResult {
        val duration = System.currentTimeMillis() - startTime
        val status = when {
            loop.timedOut -> BATaskStatus.TIMEOUT
            loop.toolCallsFailed > 0 -> BATaskStatus.PARTIAL
            else -> BATaskStatus.SUCCESS
        }
        logger.info(
            "Legacy task complete: duration={}ms, toolCalls={}, failed={}, backend={}",
            duration, loop.toolCallsExecuted, loop.toolCallsFailed, config.cliBackend
        )
        return BATaskResult(
            document = loop.document,
            toolCallsExecuted = loop.toolCallsExecuted,
            toolCallsFailed = loop.toolCallsFailed,
            totalDurationMs = duration,
            status = status,
            toolCallLog = loop.toolCallLog
        )
    }

    companion object {
        private const val AGENT_TYPE = "ba-agent"
    }
}
