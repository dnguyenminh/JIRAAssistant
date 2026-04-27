package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.subprocess.MessageProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Event-driven loop that reads AI subprocess stdout, detects tool
 * call requests, proxies them through [SubprocessProxy], and
 * accumulates the final document response.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8
 */
class ToolCallLoopEngine(
    private val subprocessProxy: SubprocessProxy,
    private val progressReporter: ProgressReporter
) {

    private val logger = LoggerFactory.getLogger(ToolCallLoopEngine::class.java)

    /**
     * Runs the tool call loop reading from [stdoutFlow] and writing
     * responses via [stdinWriter]. Returns when `---END---` is received
     * or [timeoutSeconds] elapses.
     */
    suspend fun runLoop(
        stdoutFlow: Flow<String>,
        stdinWriter: suspend (String) -> Unit,
        maxToolCalls: Int = 30,
        timeoutSeconds: Int = 180
    ): ToolCallLoopResult {
        val state = LoopState(maxToolCalls)
        val result = withTimeoutOrNull(timeoutSeconds * 1000L) {
            collectLines(stdoutFlow, stdinWriter, state)
        }
        val timedOut = result == null
        return state.toResult(timedOut)
    }

    private suspend fun collectLines(
        stdoutFlow: Flow<String>,
        stdinWriter: suspend (String) -> Unit,
        state: LoopState
    ) {
        stdoutFlow
            .takeWhile { !MessageProtocol.isDelimiter(it) }
            .collect { line -> processLine(line, stdinWriter, state) }
    }

    private suspend fun processLine(
        line: String,
        stdinWriter: suspend (String) -> Unit,
        state: LoopState
    ) {
        val message = MessageProtocol.parseStdoutLine(line)
        val toolCall = message?.toolCall
        if (message?.type == "toolCall" && toolCall != null) {
            handleToolCall(toolCall, stdinWriter, state)
        } else {
            state.appendDocument(line)
        }
    }

    private suspend fun handleToolCall(
        request: ToolCallRequest,
        stdinWriter: suspend (String) -> Unit,
        state: LoopState
    ) {
        logger.info("BA tool call request: id={}, tool={}, args={}",
            request.id, request.name, request.arguments)
        val startTime = System.currentTimeMillis()
        val response = if (state.isLimitReached()) {
            buildLimitExceededResponse(request.id)
        } else {
            proxyToolCall(request, state)
        }
        val duration = System.currentTimeMillis() - startTime
        logToolCallResponse(request, response, duration)
        state.logToolCall(request.name, duration, response.success, response)
        reportProgress(state)
        val formatted = MessageProtocol.formatToolResponse(response)
        stdinWriter(formatted)
    }

    private fun logToolCallResponse(
        request: ToolCallRequest,
        response: ToolCallResponse,
        duration: Long
    ) {
        logger.info(
            "BA tool call response: id={}, tool={}, success={}, duration={}ms, responseSize={} chars",
            request.id, request.name, response.success, duration, response.data.length
        )
        logger.debug("BA tool call response data: {}", response.data.take(500))
    }

    private suspend fun proxyToolCall(
        request: ToolCallRequest,
        state: LoopState
    ): ToolCallResponse {
        state.incrementExecuted()
        val response = subprocessProxy.handleToolCallRequest(request)
        if (!response.success) state.incrementFailed()
        return response
    }

    private fun buildLimitExceededResponse(id: String) = ToolCallResponse(
        id = id,
        success = false,
        error = "Tool call limit exceeded. " +
            "Produce your final response with available data."
    )

    private suspend fun reportProgress(state: LoopState) {
        val percent = (15 + (state.totalHandled * 65 / 30))
            .coerceIn(15, 80)
        progressReporter.reportToolCall(
            state.lastToolName,
            if (state.lastSuccess) "completed" else "failed"
        )
        progressReporter.reportProgress(
            percent, "Tool call: ${state.lastToolName}"
        )
    }
}

/**
 * Result of the tool call loop execution.
 */
data class ToolCallLoopResult(
    val document: String,
    val toolCallsExecuted: Int,
    val toolCallsFailed: Int,
    val toolCallLog: List<ToolCallLogEntry>,
    val timedOut: Boolean
)

/**
 * Mutable state accumulated during the loop.
 */
private class LoopState(private val maxToolCalls: Int) {
    private val documentLines = mutableListOf<String>()
    private val log = mutableListOf<ToolCallLogEntry>()
    private var executed = 0
    private var failed = 0
    var totalHandled = 0; private set
    var lastToolName = ""; private set
    var lastSuccess = true; private set

    fun appendDocument(line: String) { documentLines.add(line) }
    fun isLimitReached() = executed >= maxToolCalls
    fun incrementExecuted() { executed++ }
    fun incrementFailed() { failed++ }

    fun logToolCall(
        name: String, durationMs: Long,
        success: Boolean, response: ToolCallResponse
    ) {
        totalHandled++
        lastToolName = name
        lastSuccess = success
        log.add(
            ToolCallLogEntry(
                toolName = name,
                durationMs = durationMs,
                success = success,
                resultSizeChars = response.data.length
            )
        )
    }

    fun toResult(timedOut: Boolean) = ToolCallLoopResult(
        document = documentLines.joinToString("\n"),
        toolCallsExecuted = executed,
        toolCallsFailed = failed,
        toolCallLog = log.toList(),
        timedOut = timedOut
    )
}
