package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import com.assistant.server.agent.ba.subprocess.pipeline.cli.CliInteractiveEngine
import com.assistant.server.agent.ba.subprocess.pipeline.cli.CliToolExecutor
import com.assistant.server.agent.ba.subprocess.pipeline.cli.MasterPromptBuilder
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.InteractiveSessionContext
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopResult
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * CLI interactive pipeline strategy for BA document generation.
 *
 * Spawns a CLI process, sends a single master prompt, runs an interactive
 * tool-call loop, and returns the generated document as [BATaskResult].
 *
 * Drop-in replacement for [MultiTurnPipelineStrategy] via [PipelineStrategy].
 */
class CliInteractiveStrategy(
    private val subprocessProxy: SubprocessProxy,
    private val cliBackendResolver: CliBackendResolver
) : PipelineStrategy {

    private val logger = LoggerFactory.getLogger(CliInteractiveStrategy::class.java)
    private val engine = CliInteractiveEngine()

    override suspend fun execute(
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ): BATaskResult {
        val resolveResult = resolveBackend(config)
        if (resolveResult.isFailure) {
            return buildFailedResult(resolveResult.exceptionOrNull()?.message ?: "Unknown resolve error")
        }

        val subprocessConfig = resolveResult.getOrThrow()
        progressReporter.reportProgress(5, "CLI process starting")
        logger.info("Starting CLI session: ticket={}, backend={}, docType={}", config.rootTicketId, config.cliBackend, config.docType)

        val masterPrompt = buildPrompt(config)
        val sessionContext = InteractiveSessionContext()

        return runSession(subprocessConfig, masterPrompt, config, sessionContext, progressReporter)
    }

    private suspend fun resolveBackend(config: BATaskConfig) =
        cliBackendResolver.resolve(config.cliBackend)

    private fun buildPrompt(config: BATaskConfig): String =
        MasterPromptBuilder.build(
            ticketId = config.rootTicketId,
            docType = config.docType,
            availableTools = subprocessProxy.getAvailableToolDescriptors()
        )

    private suspend fun runSession(
        subprocessConfig: com.assistant.agent.subprocess.SubprocessConfig,
        masterPrompt: String,
        config: BATaskConfig,
        sessionContext: InteractiveSessionContext,
        progressReporter: ProgressReporter
    ): BATaskResult {
        val process: Process
        try {
            process = engine.startProcess(subprocessConfig)
        } catch (e: IOException) {
            logger.error("Failed to start CLI process: {}", e.message, e)
            return buildFailedResult("Process failed to start: ${e.message}")
        }

        return try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            executeInteractiveLoop(reader, writer, masterPrompt, config, sessionContext, progressReporter)
        } finally {
            engine.terminateProcess(process)
        }
    }

    private suspend fun executeInteractiveLoop(
        reader: BufferedReader,
        writer: BufferedWriter,
        masterPrompt: String,
        config: BATaskConfig,
        sessionContext: InteractiveSessionContext,
        progressReporter: ProgressReporter
    ): BATaskResult {
        progressReporter.reportProgress(10, "Prompt sent")
        engine.sendToStdin(writer, masterPrompt)

        val executor = CliToolExecutor(subprocessProxy)
        val loopConfig = LoopConfig(config.maxToolCalls, config.taskTimeoutSeconds)
        val loopResult = engine.runInteractiveLoop(reader, writer, executor, sessionContext, loopConfig)

        progressReporter.reportProgress(90, "Document received")
        val status = determineStatus(loopResult)
        val result = sessionContext.toBATaskResult(status)

        logCompletion(config, result)
        progressReporter.reportProgress(100, "Complete")
        return result
    }

    private fun determineStatus(loopResult: LoopResult): BATaskStatus = when {
        loopResult.timedOut -> BATaskStatus.TIMEOUT
        loopResult.document.isBlank() -> BATaskStatus.FAILED
        loopResult.toolCallsFailed > 0 -> BATaskStatus.PARTIAL
        else -> BATaskStatus.SUCCESS
    }

    private fun buildFailedResult(errorMessage: String): BATaskResult {
        logger.error("CLI interactive session failed: {}", errorMessage)
        return BATaskResult(
            document = "",
            toolCallsExecuted = 0,
            toolCallsFailed = 0,
            totalDurationMs = 0L,
            status = BATaskStatus.FAILED
        )
    }

    private fun logCompletion(config: BATaskConfig, result: BATaskResult) {
        logger.info(
            "CLI session complete: ticket={}, status={}, tools={}/{}, docSize={}",
            config.rootTicketId, result.status,
            result.toolCallsExecuted - result.toolCallsFailed, result.toolCallsExecuted,
            result.document.length
        )
    }
}
