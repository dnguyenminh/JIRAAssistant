package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Orchestrates multi-phase BRD generation pipeline.
 * Detects KB availability → multi-phase (3 AI phases + assembly)
 * or single-phase fallback when KB unavailable.
 */
class PipelineOrchestrator(
    private val loopRunner: AgenticLoopRunner,
    private val promptBuilder: AgenticPromptBuilder,
    private val phasePromptBuilder: PhasePromptBuilder,
    private val phaseToolFilter: PhaseToolFilter,
    private val assembler: BrdAssembler,
    private val toolBridge: ToolExecutionBridge,
    private val kbRepository: com.assistant.kb.KBRepository? = null,
    private val localKBToolExecutor: com.assistant.server.chat.LocalKBToolExecutor? = null,
    private val jiraContentExtractor: com.assistant.ai.deepanalysis.JiraContentExtractor? = null,
    private val vectorStore: com.assistant.server.attachment.VectorStore? = null
) {
    private val log = LoggerFactory.getLogger(PipelineOrchestrator::class.java)

    suspend fun executePipeline(
        backend: AiBackend, config: PipelineConfig, reporter: ProgressReporter
    ): AgenticLoopResult {
        val hasKb = phaseToolFilter.hasKbTools(config.allTools)
        log.info("Pipeline mode: {}, tools={}", if (hasKb) "multi-phase" else "single-phase", config.allTools.size)
        val interactionLog = PipelineInteractionLogger.create(config.ticketId)
        return if (hasKb) executeMultiPhase(backend, config, reporter, interactionLog)
        else executeSinglePhase(backend, config, reporter)
    }
    private suspend fun executeSinglePhase(
        backend: AiBackend, config: PipelineConfig, reporter: ProgressReporter
    ): AgenticLoopResult {
        val loopConfig = AgenticLoopConfig(
            ticketId = config.ticketId, docType = config.docType,
            maxToolCalls = config.phase1MaxToolCalls + config.phase2MaxToolCalls + config.phase3MaxToolCalls,
            taskTimeoutSeconds = config.phase1TimeoutSeconds + config.phase2TimeoutSeconds + config.phase3TimeoutSeconds
        )
        return loopRunner.runLoop(backend, loopConfig, reporter)
    }
    private suspend fun executeMultiPhase(
        backend: AiBackend, config: PipelineConfig, reporter: ProgressReporter,
        iLog: PipelineInteractionLogger.InteractionLog
    ): AgenticLoopResult {
        val startTime = System.currentTimeMillis()
        // Code-driven data collection — no AI needed
        reporter.reportProgress(5, "Pre-fetching ticket data from KB + Jira")
        iLog.logPhaseStart(PhaseId.DATA_COLLECTION, 0)
        val preFetcher = DataPreFetcher(kbRepository!!, localKBToolExecutor, jiraContentExtractor, vectorStore)
        val collectedData = preFetcher.fetchAll(config.ticketId, iLog)
        iLog.logPhaseEnd(PhaseId.DATA_COLLECTION, collectedData.length, 0)
        log.info("DataPreFetcher collected {} chars for {}", collectedData.length, config.ticketId)
        reporter.reportProgress(30, "Data collected, starting BRD writing + diagrams")
        val (phase2, phase3) = executePhase2And3(backend, config, collectedData, reporter, iLog)
        logPhaseDuration(phase2)
        phase3?.let { logPhaseDuration(it) }
        reporter.reportProgress(70, "Assembly: merging BRD + diagrams")
        val assembled = assembler.assemble(phase2.output, phase3?.output)
        iLog.logAssembly(phase2.output.length, phase3?.output?.length ?: 0, assembled.length)
        val totalDuration = System.currentTimeMillis() - startTime
        log.info("Pipeline total duration: {}ms", totalDuration)
        reporter.reportProgress(100, "Pipeline complete")
        return aggregateResults2(phase2, phase3, assembled, totalDuration)
    }
    private suspend fun executePhase2And3(
        backend: AiBackend, config: PipelineConfig, phase1Output: String, reporter: ProgressReporter,
        iLog: PipelineInteractionLogger.InteractionLog
    ): Pair<PhaseResult, PhaseResult?> {
        // Always sequential — Ollama has shared conversation history, parallel causes conflicts
        return executeSequential(backend, config, phase1Output, reporter, iLog)
    }
    private suspend fun executeParallel(
        backend: AiBackend, config: PipelineConfig, phase1Output: String, reporter: ProgressReporter,
        iLog: PipelineInteractionLogger.InteractionLog
    ): Pair<PhaseResult, PhaseResult?> = coroutineScope {
        val p2 = async { executePhaseWithRetry(backend, config, PhaseId.BRD_WRITING, config.maxRetries, reporter, phase1Output, iLog) }
        val p3 = async { executePhaseWithRetry(backend, config, PhaseId.DIAGRAM_GENERATION, 0, reporter, phase1Output, iLog) }
        Pair(p2.await(), p3.await())
    }

    private suspend fun executeSequential(
        backend: AiBackend, config: PipelineConfig, phase1Output: String, reporter: ProgressReporter,
        iLog: PipelineInteractionLogger.InteractionLog
    ): Pair<PhaseResult, PhaseResult?> {
        val p2 = executePhaseWithRetry(backend, config, PhaseId.BRD_WRITING, config.maxRetries, reporter, phase1Output, iLog)
        val p3 = executePhaseWithRetry(backend, config, PhaseId.DIAGRAM_GENERATION, 0, reporter, phase1Output, iLog)
        return Pair(p2, p3)
    }

    private suspend fun executePhaseWithRetry(
        backend: AiBackend, config: PipelineConfig, phaseId: PhaseId,
        maxRetries: Int, reporter: ProgressReporter, phase1Output: String = "",
        iLog: PipelineInteractionLogger.InteractionLog? = null
    ): PhaseResult {
        var result = executePhase(backend, config, phaseId, reporter, phase1Output, iLog)
        if (!result.success && maxRetries > 0) {
            log.warn("Phase {} failed, retrying (1/{})", phaseId, maxRetries)
            result = executePhase(backend, config, phaseId, reporter, phase1Output, iLog)
        }
        return result
    }

    private suspend fun executePhase(
        backend: AiBackend, config: PipelineConfig, phaseId: PhaseId,
        reporter: ProgressReporter, phase1Output: String = "",
        iLog: PipelineInteractionLogger.InteractionLog? = null
    ): PhaseResult {
        val pc = buildPhaseConfig(config, phaseId)
        val prompt = buildPhasePrompt(phaseId, config.ticketId, config.docType, filterToolsForPhase(config, phaseId), phase1Output)
        iLog?.logPhaseStart(phaseId, prompt.length)
        iLog?.logPrompt("INITIAL_PROMPT", prompt)
        val startTime = System.currentTimeMillis()
        val toolLog = mutableListOf<ToolCallLogEntry>()
        var fails = 0
        val output = withTimeoutOrNull(pc.timeoutSeconds * 1000L) {
            runPhaseLoop(backend, prompt, pc.maxToolCalls, toolLog, reporter, iLog) { fails++ }
        }
        val duration = System.currentTimeMillis() - startTime
        iLog?.logPhaseEnd(phaseId, output?.length ?: 0, toolLog.size)
        return PhaseResult(phaseId, output?.trim() ?: "", toolLog, toolLog.size, fails, duration,
            success = output != null && output.isNotBlank(), timedOut = output == null)
    }

    private suspend fun runPhaseLoop(
        backend: AiBackend, prompt: String, maxCalls: Int,
        toolLog: MutableList<ToolCallLogEntry>, reporter: ProgressReporter,
        iLog: PipelineInteractionLogger.InteractionLog? = null, onFail: () -> Unit
    ): String {
        backend.startSession()
        var lastTextResponse = ""
        try {
            var response = backend.sendMessage(prompt).response
            iLog?.logResponse(response)
            while (toolLog.size < maxCalls) {
                val req = parseToolCall(response, backend)
                if (req != null) {
                    iLog?.logToolCall(req.tool, req.params)
                    val result = toolBridge.execute(req)
                    toolLog.add(result.logEntry)
                    iLog?.logToolResult(req.tool, result.rawResponse.success, result.rawResponse.data.length)
                    if (!result.rawResponse.success) onFail()
                    reporter.reportToolCall(req.tool, if (result.rawResponse.success) "success" else "failed")
                    val continuation = phasePromptBuilder.buildPhaseContinuation(result.formattedResult)
                    iLog?.logPrompt("CONTINUATION", continuation)
                    response = backend.sendMessage(continuation).response
                    iLog?.logResponse(response)
                    // Save non-tool-call text as fallback
                    if (parseToolCall(response, backend) == null && response.length > lastTextResponse.length) {
                        lastTextResponse = response
                    }
                } else { return response }
            }
            // maxCalls reached — return last meaningful text response
            return if (response.isNotBlank()) response else lastTextResponse
        } finally {
            if (backend.isSessionActive()) backend.endSession()
        }
    }

    private fun parseToolCall(response: String, backend: AiBackend): ToolRequest? {
        if (backend.isToolCall(response)) {
            val parsed = backend.parseToolCall(response)
            if (parsed?.type == "tool_call") return parsed
        }
        val match = TOOL_CALL_REGEX.find(response) ?: return null
        return try {
            val p = LENIENT_JSON.decodeFromString<ToolRequest>(match.value)
            if (p.type == "tool_call") p else null
        } catch (_: Exception) { null }
    }
    private fun buildPhaseConfig(config: PipelineConfig, id: PhaseId) = PhaseConfig(
        id, config.ticketId, config.docType,
        when (id) { PhaseId.DATA_COLLECTION -> config.phase1MaxToolCalls; PhaseId.BRD_WRITING -> config.phase2MaxToolCalls; PhaseId.DIAGRAM_GENERATION -> config.phase3MaxToolCalls },
        when (id) { PhaseId.DATA_COLLECTION -> config.phase1TimeoutSeconds; PhaseId.BRD_WRITING -> config.phase2TimeoutSeconds; PhaseId.DIAGRAM_GENERATION -> config.phase3TimeoutSeconds }
    )

    private fun filterToolsForPhase(config: PipelineConfig, id: PhaseId) = when (id) {
        PhaseId.DATA_COLLECTION -> phaseToolFilter.filterForPhase1(config.allTools)
        PhaseId.BRD_WRITING -> phaseToolFilter.filterForPhase2(config.allTools)
        PhaseId.DIAGRAM_GENERATION -> phaseToolFilter.filterForPhase3(config.allTools) }
    private fun buildPhasePrompt(id: PhaseId, ticketId: String, docType: String, tools: List<ToolDescriptor>, phase1Output: String = "") = when (id) {
        PhaseId.DATA_COLLECTION -> phasePromptBuilder.buildPhase1Prompt(ticketId, tools)
        PhaseId.BRD_WRITING -> phasePromptBuilder.buildPhase2Prompt(ticketId, docType, tools, phase1Output)
        PhaseId.DIAGRAM_GENERATION -> phasePromptBuilder.buildPhase3Prompt(ticketId, tools, phase1Output) }
    private fun aggregateResults(p1: PhaseResult, p2: PhaseResult, p3: PhaseResult?, doc: String, totalMs: Long) = AgenticLoopResult(
        doc, (p1.toolCallLog + p2.toolCallLog + (p3?.toolCallLog ?: emptyList())).let { it },
        p1.toolCallLog.size + p2.toolCallLog.size + (p3?.toolCallLog?.size ?: 0),
        p1.toolCallLog.count { !it.success } + p2.toolCallLog.count { !it.success } + (p3?.toolCallLog?.count { !it.success } ?: 0), false, totalMs)
    private fun aggregateResults2(p2: PhaseResult, p3: PhaseResult?, doc: String, totalMs: Long) = AgenticLoopResult(
        doc, (p2.toolCallLog + (p3?.toolCallLog ?: emptyList())),
        p2.toolCallLog.size + (p3?.toolCallLog?.size ?: 0),
        p2.toolCallLog.count { !it.success } + (p3?.toolCallLog?.count { !it.success } ?: 0), false, totalMs)
    private fun buildFailedResult(phase: PhaseResult, startTime: Long) = AgenticLoopResult(
        "", phase.toolCallLog, phase.toolCallsExecuted, phase.toolCallsFailed, phase.timedOut, System.currentTimeMillis() - startTime)
    private fun logPhaseDuration(p: PhaseResult) = log.info("Phase {} completed: success={}, duration={}ms, tools={}", p.phaseId, p.success, p.durationMs, p.toolCallsExecuted)

    companion object {
        private const val MIN_PHASE1_TOOLS = 5
        private val TOOL_CALL_REGEX =
            """\{[^{}]*"type"\s*:\s*"tool_call"[^{}]*(?:\{[^{}]*\}[^{}]*)?\}""".toRegex()
        private val LENIENT_JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
