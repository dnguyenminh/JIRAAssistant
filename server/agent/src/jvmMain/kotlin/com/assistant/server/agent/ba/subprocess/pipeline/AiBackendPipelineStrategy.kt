package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.mcp.McpProcessManager
import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import com.assistant.server.chat.ChatLocalKBContext
import com.assistant.server.chat.LocalKBToolExecutor
import com.assistant.server.mcp.internal.InternalMcpBridge
import com.assistant.settings.SettingsRepository
import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.AgenticLoopRunner
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.AgenticPromptBuilder
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.AiBackend
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.AiBackendFactory
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.BrdAssembler
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.LocalKBToolDescriptorProvider
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.PhasePromptBuilder
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.PhaseToolFilter
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.PipelineOrchestrator
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ToolExecutionBridge
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopResult
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.PipelineConfig
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClient
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.toOllamaTool
import org.slf4j.LoggerFactory

/**
 * Pipeline strategy that uses the POC's AiBackend-based agentic loop
 * for BA document generation.
 *
 * Replaces [CliInteractiveStrategy] as the default strategy.
 *
 * Requirements: 9.1, 9.2, 9.3, 9.7, 11.1–11.5, 12.1–12.6
 */
class AiBackendPipelineStrategy(
    private val subprocessProxy: SubprocessProxy,
    private val cliBackendResolver: CliBackendResolver,
    private val mcpProcessManager: McpProcessManager? = null,
    private val internalMcpBridge: InternalMcpBridge? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val localKBToolExecutor: LocalKBToolExecutor? = null,
    private val kbRepository: com.assistant.kb.KBRepository? = null,
    private val jiraContentExtractor: com.assistant.ai.deepanalysis.JiraContentExtractor? = null,
    private val vectorStore: com.assistant.server.attachment.VectorStore? = null
) : PipelineStrategy {

    private val log = LoggerFactory.getLogger(
        AiBackendPipelineStrategy::class.java
    )

    override suspend fun execute(
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ): BATaskResult {
        val startTime = System.currentTimeMillis()
        return try {
            doExecute(config, progressReporter, startTime)
        } catch (e: kotlinx.coroutines.CancellationException) {
            log.info("Strategy cancelled via CancellationException")
            cancelActiveBackend()
            throw e
        } catch (e: Exception) {
            // When httpClient.close() aborts a request, it throws IOException/ClosedChannelException
            // Check if this was caused by cancel
            val wasCancelled = (activeBackend as? com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClient)?.let {
                try { it.checkCancelled(); false } catch (_: Exception) { true }
            } ?: false
            if (wasCancelled) {
                log.info("Strategy cancelled (HTTP client closed)")
                throw kotlinx.coroutines.CancellationException("Job cancelled")
            }
            log.error("Strategy execution failed: {}", e.message, e)
            buildFailedResult(e.message ?: "Unknown error", startTime)
        }
    }

    private fun cancelActiveBackend() {
        (activeBackend as? com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClient)?.cancel()
    }

    /** Track active backend for cancellation. */
    private var activeBackend: Any? = null

    private suspend fun doExecute(
        config: BATaskConfig,
        reporter: ProgressReporter,
        startTime: Long
    ): BATaskResult {
        val backend = createBackend(config)
            ?: return buildFailedResult("Backend creation failed", startTime)

        if (!checkInstalled(backend, startTime)) {
            return buildNotInstalledResult(backend, startTime)
        }

        configureOllamaTools(backend).let { configuredBackend ->
            activeBackend = configuredBackend
            reporter.reportProgress(5, "AI backend starting")

            val bridge = ToolExecutionBridge(subprocessProxy, reporter, mcpProcessManager, internalMcpBridge, localKBToolExecutor)
            val promptBuilder = AgenticPromptBuilder(subprocessProxy, mcpProcessManager, internalMcpBridge)
            val runner = AgenticLoopRunner(bridge, promptBuilder)
            val orchestrator = PipelineOrchestrator(
                runner, promptBuilder, PhasePromptBuilder(),
                PhaseToolFilter(), BrdAssembler(), bridge,
                kbRepository, localKBToolExecutor, jiraContentExtractor, vectorStore
            )

            val allToolDescriptors = collectToolDescriptors()
            val pipelineConfig = PipelineConfig(
                ticketId = config.rootTicketId,
                docType = config.docType,
                allTools = allToolDescriptors
            )

            reporter.reportProgress(10, "Pipeline starting")
            val loopResult = orchestrator.executePipeline(configuredBackend, pipelineConfig, reporter)

            reporter.reportProgress(90, "Document received")
            val result = buildTaskResult(loopResult, startTime)

            reporter.reportProgress(100, "Complete")
            logCompletion(config, result)
            return result
        }
    }

    /**
     * Collect all tool descriptors from MCP bridges.
     * Same logic as AgenticPromptBuilder.getAllToolDescriptors()
     * but accessible here without modifying that class.
     */
    private fun collectToolDescriptors(): List<ToolDescriptor> {
        val externalTools = mcpProcessManager?.getActiveTools() ?: emptyList()
        if (externalTools.isNotEmpty()) {
            val descriptors = externalTools.map { tool ->
                ToolDescriptor(
                    name = "mcp_${tool.serverName}_${tool.name}",
                    description = tool.description
                )
            }
            return descriptors + collectLocalKBDescriptors()
        }
        return collectLocalKBDescriptors().ifEmpty { subprocessProxy.getAvailableToolDescriptors() }
    }

    /** Append Local KB descriptors when enabled. */
    private fun collectLocalKBDescriptors(): List<ToolDescriptor> {
        if (localKBToolExecutor == null) return emptyList()
        if (!ChatLocalKBContext.isEnabled(settingsRepository)) return emptyList()
        return LocalKBToolDescriptorProvider.getDescriptors()
    }

    private suspend fun createBackend(config: BATaskConfig): AiBackend? {
        val factory = AiBackendFactory(cliBackendResolver)
        val result = factory.create(config.cliBackend)
        if (result.isFailure) {
            log.error("Backend creation failed: {}", result.exceptionOrNull()?.message)
            return null
        }
        return result.getOrThrow()
    }

    private fun checkInstalled(backend: AiBackend, startTime: Long): Boolean {
        if (!backend.isInstalled()) {
            log.warn("Backend '{}' is not installed", backend.displayName)
            return false
        }
        return true
    }

    private fun configureOllamaTools(backend: AiBackend): AiBackend {
        if (backend is OllamaApiClient) {
            // Combine internal tools (Jira Assistant UI, 30 tools)
            // + external tools (from McpProcessManager/database)
            val internalTools = internalMcpBridge?.getAggregatedTools() ?: emptyList()
            val externalTools = mcpProcessManager?.getActiveTools() ?: emptyList()
            val allMcpTools = internalTools + externalTools

            val allOllamaTools = allMcpTools.map { it.toOllamaTool() }
            val eligible = allOllamaTools.filter { tool ->
                EXCLUDED_PATTERNS.none { p ->
                    tool.function.name.contains(p, ignoreCase = true)
                }
            }
            log.info(
                "Configured ${eligible.size} Ollama tools (${internalTools.size} internal + ${externalTools.size} external, excluded ${allOllamaTools.size - eligible.size})"
            )
            return OllamaApiClient(
                baseUrl = backend.baseUrl,
                model = backend.model,
                tools = eligible,
                streaming = false,
                httpClient = backend.httpClient
            )
        }
        return backend
    }

    companion object {
        /** Tools to exclude — browser-based or irrelevant for BA. */
        private val EXCLUDED_PATTERNS = listOf(
            "playwright", "browser",
            "convert_to_markdown"
        )
    }

    private fun buildTaskResult(
        loopResult: AgenticLoopResult, startTime: Long
    ): BATaskResult {
        val duration = System.currentTimeMillis() - startTime
        val status = AgenticLoopRunner.determineStatus(loopResult)
        return BATaskResult(
            document = loopResult.document,
            toolCallsExecuted = loopResult.toolCallsExecuted,
            toolCallsFailed = loopResult.toolCallsFailed,
            totalDurationMs = duration,
            status = status,
            toolCallLog = loopResult.toolCallLog
        )
    }

    private fun buildNotInstalledResult(
        backend: AiBackend, startTime: Long
    ): BATaskResult {
        val duration = System.currentTimeMillis() - startTime
        return BATaskResult(
            document = backend.getInstallInstructions(),
            toolCallsExecuted = 0,
            toolCallsFailed = 0,
            totalDurationMs = duration,
            status = BATaskStatus.FAILED
        )
    }

    private fun buildFailedResult(
        reason: String, startTime: Long
    ): BATaskResult {
        val duration = System.currentTimeMillis() - startTime
        log.error("Strategy failed: {}", reason)
        return BATaskResult(
            document = "",
            toolCallsExecuted = 0,
            toolCallsFailed = 0,
            totalDurationMs = duration,
            status = BATaskStatus.FAILED
        )
    }

    private fun logCompletion(config: BATaskConfig, result: BATaskResult) {
        log.info(
            "Strategy complete: ticket={}, status={}, tools={}, docSize={}",
            config.rootTicketId, result.status,
            result.toolCallsExecuted, result.document.length
        )
    }
}
