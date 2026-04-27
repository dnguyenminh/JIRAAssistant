package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.server.agent.ba.subprocess.pipeline.AiBackendPipelineStrategy
import com.assistant.server.chat.LocalKBToolExecutor
import com.assistant.server.mcp.internal.InternalMcpBridge
import com.assistant.server.agent.ba.subprocess.pipeline.DataCollector
import com.assistant.server.agent.ba.subprocess.pipeline.DocumentAssembler
import com.assistant.server.agent.ba.subprocess.pipeline.MultiTurnPipelineStrategy
import com.assistant.server.agent.ba.subprocess.pipeline.PipelineStopCondition
import com.assistant.server.agent.ba.subprocess.pipeline.PipelineStrategy
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Orchestrates BA document generation via AI subprocess.
 *
 * Delegates execution to a [PipelineStrategy] — defaults to
 * [AiBackendPipelineStrategy]. Falls back to [MultiTurnPipelineStrategy]
 * via the legacy companion overload.
 *
 * Requirements: 7.1, 7.2, 9.1, 10.1, 10.2, 10.3, 10.6
 */
open class BASubprocessOrchestrator(
    private val subprocessManager: SubprocessManager,
    private val subprocessProxy: SubprocessProxy,
    private val progressReporter: ProgressReporter,
    private val settingsRepository: SettingsRepository,
    private val providerConfigRepo: ProviderConfigRepository? = null,
    private val mcpProcessManager: McpProcessManager? = null,
    private val internalMcpBridge: InternalMcpBridge? = null,
    private val localKBToolExecutor: LocalKBToolExecutor? = null,
    private val kbRepository: com.assistant.kb.KBRepository? = null,
    private val jiraContentExtractor: com.assistant.ai.deepanalysis.JiraContentExtractor? = null,
    private val vectorStore: com.assistant.server.attachment.VectorStore? = null,
    private val strategy: PipelineStrategy = createDefaultStrategy(
        subprocessProxy, settingsRepository, providerConfigRepo,
        mcpProcessManager, internalMcpBridge, localKBToolExecutor, kbRepository, jiraContentExtractor, vectorStore
    )
) {

    private val logger = LoggerFactory.getLogger(
        BASubprocessOrchestrator::class.java
    )

    companion object {
        private const val AGENT_TYPE = "ba-agent"

        /**
         * Creates the default [AiBackendPipelineStrategy].
         * Requirements: 13.1
         */
        fun createDefaultStrategy(
            subprocessProxy: SubprocessProxy,
            settingsRepository: SettingsRepository,
            providerConfigRepo: ProviderConfigRepository?,
            mcpProcessManager: McpProcessManager? = null,
            internalMcpBridge: InternalMcpBridge? = null,
            localKBToolExecutor: LocalKBToolExecutor? = null,
            kbRepository: com.assistant.kb.KBRepository? = null,
            jiraContentExtractor: com.assistant.ai.deepanalysis.JiraContentExtractor? = null,
            vectorStore: com.assistant.server.attachment.VectorStore? = null
        ): PipelineStrategy = AiBackendPipelineStrategy(
            subprocessProxy,
            CliBackendResolver(settingsRepository, providerConfigRepo),
            mcpProcessManager,
            internalMcpBridge,
            settingsRepository,
            localKBToolExecutor,
            kbRepository,
            jiraContentExtractor,
            vectorStore
        )

        /**
         * Legacy overload — creates [MultiTurnPipelineStrategy].
         * Retained for backward compatibility and fallback.
         */
        fun createMultiTurnStrategy(
            subprocessManager: SubprocessManager,
            subprocessProxy: SubprocessProxy,
            progressReporter: ProgressReporter
        ): PipelineStrategy {
            val dataCollector = DataCollector(subprocessProxy, progressReporter)
            val stopCondition = PipelineStopCondition()
            val documentAssembler = DocumentAssembler()
            return MultiTurnPipelineStrategy(
                dataCollector, stopCondition, documentAssembler, subprocessManager
            )
        }
    }

    /** Executes a BA document generation task via AI subprocess. */
    open suspend fun executeTask(config: BATaskConfig): BATaskResult {
        val startTime = System.currentTimeMillis()
        return try {
            doExecuteTask(config, startTime)
        } catch (e: Exception) {
            buildFailedResult(e.message ?: "Unknown error", startTime)
        }
    }

    private suspend fun doExecuteTask(
        config: BATaskConfig, startTime: Long
    ): BATaskResult {
        // Ollama uses REST API — skip CLI path resolution
        if (config.cliBackend != "ollama") {
            val resolved = CliBackendResolver(settingsRepository, providerConfigRepo)
                .resolve(config.cliBackend)
            if (resolved.isFailure) {
                return buildFailedResult(
                    resolved.exceptionOrNull()!!.message ?: "CLI failed",
                    startTime
                )
            }
            registerSubprocessConfig(resolved.getOrThrow())
        }
        progressReporter.reportProgress(5, "Subprocess started")
        val stderrJob = launchStderrCapture()
        val result = strategy.execute(config, progressReporter)
        stderrJob.cancel()
        logCompletion(result, config, startTime)
        return result
    }

    private fun launchStderrCapture(): Job =
        CoroutineScope(Dispatchers.IO).launch {
            // Stderr captured by SubprocessManager's internal
            // coroutine (SubprocessManagerHelpers). Placeholder
            // for orchestrator-level stderr processing.
        }

    private fun registerSubprocessConfig(
        config: com.assistant.agent.subprocess.SubprocessConfig
    ) {
        val mgr = subprocessManager
        if (mgr is com.assistant.server.agent.subprocess.SubprocessManagerImpl) {
            mgr.registerConfig(AGENT_TYPE, config)
        }
    }

    private fun logCompletion(
        result: BATaskResult, config: BATaskConfig, startTime: Long
    ) {
        val duration = System.currentTimeMillis() - startTime
        logger.info(
            "Task complete: duration={}ms, toolCalls={}, failed={}, " +
                "docSize={}, backend={}, status={}",
            duration, result.toolCallsExecuted, result.toolCallsFailed,
            result.document.length, config.cliBackend, result.status
        )
    }

    private fun buildFailedResult(
        reason: String, startTime: Long
    ): BATaskResult {
        val duration = System.currentTimeMillis() - startTime
        logger.error("Task failed: {}", reason)
        return BATaskResult(
            document = "",
            toolCallsExecuted = 0,
            toolCallsFailed = 0,
            totalDurationMs = duration,
            status = BATaskStatus.FAILED
        )
    }
}
