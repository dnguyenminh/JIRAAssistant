package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.progress.ProgressReporter
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopResult
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Core agentic loop logic ported from POC's `BrdAgent.generateBrd()`.
 *
 * Iteratively sends prompts to an [AiBackend], detects tool calls,
 * executes them via [ToolExecutionBridge], and feeds results back
 * until the AI produces a final document or limits are reached.
 */
class AgenticLoopRunner(
    private val toolBridge: ToolExecutionBridge,
    private val promptBuilder: AgenticPromptBuilder
) {

    private val log = LoggerFactory.getLogger(AgenticLoopRunner::class.java)

    /**
     * Runs the agentic loop with the given backend and config.
     */
    suspend fun runLoop(
        backend: AiBackend,
        config: AgenticLoopConfig,
        progressReporter: ProgressReporter
    ): AgenticLoopResult {
        val startTime = System.currentTimeMillis()
        val useSession = shouldUseSessionMode(backend, config)

        val result = withTimeoutOrNull(config.taskTimeoutSeconds * 1000L) {
            executeLoop(backend, config, progressReporter, useSession)
        }

        val duration = System.currentTimeMillis() - startTime
        return result?.copy(totalDurationMs = duration)
            ?: buildTimeoutResult(duration)
    }

    private suspend fun executeLoop(
        backend: AiBackend,
        config: AgenticLoopConfig,
        reporter: ProgressReporter,
        useSession: Boolean
    ): AgenticLoopResult {
        val state = LoopState(config.maxToolCalls)

        try {
            if (useSession) backend.startSession()
            val initialPrompt = promptBuilder.buildInitialPrompt(
                config.ticketId, config.docType
            )
            val firstResponse = sendToBackend(
                backend, initialPrompt, useSession
            )
            processResponse(
                firstResponse, backend, config, reporter, state, useSession
            )
        } finally {
            if (useSession && backend.isSessionActive()) {
                backend.endSession()
            }
        }

        return state.toResult()
    }

    private suspend fun processResponse(
        initialResponse: String,
        backend: AiBackend,
        config: AgenticLoopConfig,
        reporter: ProgressReporter,
        state: LoopState,
        useSession: Boolean
    ) {
        var response = initialResponse
        var retried = false

        while (state.toolCallsExecuted < config.maxToolCalls) {
            val toolRequest = tryParseToolCall(response, backend)
            if (toolRequest == null) {
                if (!retried && state.toolCallsExecuted == 0) {
                    log.warn("AI skipped tool calls — nudging to call tools first")
                    retried = true
                    response = sendToBackend(backend, NUDGE_TOOL_CALL_MSG, useSession)
                    continue
                }
                state.document = response.trim()
                return
            }
            log.info("AI requested tool: {}", toolRequest.tool)
            val toolResult = executeToolCall(
                toolRequest, state, reporter, config
            ) ?: return
            response = sendContinuation(
                backend, config, state, useSession, toolResult
            )
        }

        handleMaxToolCallsReached(backend, state, useSession)
    }

    /**
     * Try to parse a tool call from the AI response.
     * Uses 3 strategies (same as POC's BrdAgent.tryParseToolCall):
     * 1. Backend's native parseToolCall (full JSON)
     * 2. Regex search for embedded tool_call JSON in text
     * 3. Direct JSON decode
     */
    private fun tryParseToolCall(
        response: String,
        backend: AiBackend
    ): ToolRequest? {
        // Strategy 1: Backend native parsing
        if (backend.isToolCall(response)) {
            val parsed = backend.parseToolCall(response)
            if (parsed != null && parsed.type == "tool_call") return parsed
        }

        // Strategy 2: Regex search for embedded tool_call JSON
        val match = TOOL_CALL_REGEX.find(response)
        if (match != null) {
            return try {
                val parsed = LENIENT_JSON.decodeFromString<ToolRequest>(match.value)
                if (parsed.type == "tool_call") parsed else null
            } catch (_: Exception) {
                log.debug("Failed to parse embedded tool call")
                null
            }
        }

        // Strategy 3: Direct JSON decode
        return try {
            val parsed = LENIENT_JSON.decodeFromString<ToolRequest>(response)
            if (parsed.type == "tool_call") parsed else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun executeToolCall(
        toolRequest: ToolRequest,
        state: LoopState,
        reporter: ProgressReporter,
        config: AgenticLoopConfig
    ): String? {
        val bridgeResult = toolBridge.execute(toolRequest)
        state.addToolCall(bridgeResult.logEntry, bridgeResult.rawResponse.success)
        reportToolProgress(reporter, state, config.maxToolCalls)
        return bridgeResult.formattedResult
    }

    private suspend fun handleMaxToolCallsReached(
        backend: AiBackend,
        state: LoopState,
        useSession: Boolean
    ) {
        log.info("Max tool calls ({}) reached", state.maxToolCalls)
        val msg = sendToBackend(backend, PRODUCE_FINAL_DOC_MSG, useSession)
        state.document = msg.trim()
    }

    private suspend fun sendContinuation(
        backend: AiBackend,
        config: AgenticLoopConfig,
        state: LoopState,
        useSession: Boolean,
        toolResult: String
    ): String {
        state.allToolResults.add(toolResult)
        val message = buildContinuationMessage(
            useSession, toolResult, config, state
        )
        return sendToBackend(backend, message, useSession)
    }

    private fun buildContinuationMessage(
        useSession: Boolean,
        toolResult: String,
        config: AgenticLoopConfig,
        state: LoopState
    ): String = if (useSession) {
        promptBuilder.buildPersistentContinuation(toolResult)
    } else {
        promptBuilder.buildStatelessContinuation(
            config.ticketId, config.docType, state.allToolResults
        )
    }

    private fun sendToBackend(
        backend: AiBackend,
        message: String,
        useSession: Boolean
    ): String = if (useSession) {
        backend.sendMessage(message).response
    } else {
        backend.sendPrompt(message).response
    }

    private suspend fun reportToolProgress(
        reporter: ProgressReporter,
        state: LoopState,
        maxToolCalls: Int
    ) {
        val ratio = state.toolCallsExecuted.toDouble() / maxToolCalls
        val pct = (10 + (ratio * 80)).toInt().coerceIn(10, 90)
        reporter.reportProgress(
            pct, "Tool call ${state.toolCallsExecuted}/$maxToolCalls"
        )
    }

    companion object {
        private const val MAX_FAILURES = 3

        /**
         * Regex to find embedded tool_call JSON in AI response text.
         * Uses balanced brace matching up to 2 nesting levels to handle
         * nested params objects like {"type":"tool_call","params":{"k":"v"}}.
         */
        private val TOOL_CALL_REGEX =
            """\{[^{}]*"type"\s*:\s*"tool_call"[^{}]*(?:\{[^{}]*\}[^{}]*)?\}""".toRegex()

        private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

        internal const val PRODUCE_FINAL_DOC_MSG =
            "You have reached the maximum number of tool calls. " +
            "Please produce the final document now with the data " +
            "you have collected."

        internal const val NUDGE_TOOL_CALL_MSG =
            "STOP. You must call tools BEFORE writing the document. " +
            "Do NOT produce the BRD yet. " +
            "Call the get_issue tool now with the ticket ID. " +
            "Respond with ONLY the JSON tool call, nothing else."

        /**
         * Determines [BATaskStatus] from an [AgenticLoopResult].
         */
        fun determineStatus(result: AgenticLoopResult): BATaskStatus =
            when {
                result.timedOut -> BATaskStatus.TIMEOUT
                result.document.isBlank() -> BATaskStatus.FAILED
                result.toolCallsFailed > 0 -> BATaskStatus.PARTIAL
                else -> BATaskStatus.SUCCESS
            }
    }
}
