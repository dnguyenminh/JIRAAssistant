package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.subprocess.SubprocessConfig
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.InteractiveSessionContext
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopResult
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.ParsedToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit

private const val END_DELIMITER = "---END---"
private const val GRACEFUL_SHUTDOWN_SECONDS = 5L
private const val TOOL_LIMIT_MSG =
    "Tool call limit exceeded. Produce your final document now and end with ---END---"

/**
 * Manages CLI process lifecycle (spawn, stdin/stdout I/O, termination)
 * and runs the interactive tool-call loop.
 */
class CliInteractiveEngine {

    private val logger = LoggerFactory.getLogger(CliInteractiveEngine::class.java)

    /** Spawns a CLI process configured from [config]. */
    fun startProcess(config: SubprocessConfig): Process {
        val pb = ProcessBuilder(listOf(config.cliCommand) + config.cliArgs)
            .redirectErrorStream(true)
        pb.directory(java.io.File(config.workingDirectory))
        pb.environment().putAll(config.environment)
        logger.info("Starting CLI process: {} in {}", config.cliCommand, config.workingDirectory)
        return pb.start()
    }

    /** Writes [text] followed by a newline to [writer] and flushes. */
    suspend fun sendToStdin(writer: BufferedWriter, text: String) {
        withContext(Dispatchers.IO) {
            writer.write(text)
            writer.newLine()
            writer.flush()
        }
    }

    /**
     * Reads stdout line-by-line, dispatching tool calls and accumulating
     * document content until [END_DELIMITER] or timeout.
     */
    suspend fun runInteractiveLoop(
        reader: BufferedReader,
        writer: BufferedWriter,
        toolExecutor: CliToolExecutor,
        sessionContext: InteractiveSessionContext,
        config: LoopConfig
    ): LoopResult {
        val state = LoopState()

        val completed = withTimeoutOrNull(config.timeoutSeconds * 1000L) {
            readLoop(reader, writer, toolExecutor, sessionContext, config, state)
        }

        if (completed == null) {
            logger.warn("Interactive loop timed out after {}s", config.timeoutSeconds)
        }
        return state.toLoopResult(timedOut = completed == null)
    }

    /** Gracefully terminates [process]: SIGTERM → wait → force-kill. */
    fun terminateProcess(process: Process) {
        if (!process.isAlive) return
        logger.info("Terminating CLI process (graceful)")
        process.destroy()
        val exited = process.waitFor(GRACEFUL_SHUTDOWN_SECONDS, TimeUnit.SECONDS)
        if (!exited) {
            logger.warn("Process did not exit gracefully, force-killing")
            process.destroyForcibly()
        }
        logger.info("CLI process terminated")
    }

    // ── Internal loop ─────────────────────────────────────────────────

    private suspend fun readLoop(
        reader: BufferedReader,
        writer: BufferedWriter,
        toolExecutor: CliToolExecutor,
        ctx: InteractiveSessionContext,
        config: LoopConfig,
        state: LoopState
    ) {
        while (true) {
            val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
            if (line.isBlank()) continue
            if (line.contains(END_DELIMITER)) break
            processLine(line, writer, toolExecutor, ctx, state, config.maxToolCalls)
        }
    }

    private suspend fun processLine(
        line: String,
        writer: BufferedWriter,
        toolExecutor: CliToolExecutor,
        ctx: InteractiveSessionContext,
        state: LoopState,
        maxToolCalls: Int
    ) {
        val parsed = ToolCallProtocol.parseToolCall(line)
        if (parsed != null) {
            handleToolCall(parsed, writer, toolExecutor, ctx, state, maxToolCalls)
        } else {
            ctx.appendDocumentLine(line)
            state.appendLine(line)
        }
    }

    private suspend fun handleToolCall(
        parsed: ParsedToolCall,
        writer: BufferedWriter,
        toolExecutor: CliToolExecutor,
        ctx: InteractiveSessionContext,
        state: LoopState,
        maxToolCalls: Int
    ) {
        if (state.toolCallsExecuted >= maxToolCalls) {
            handleToolCallLimitExceeded(parsed.name, writer)
            return
        }
        executeAndRecord(parsed, writer, toolExecutor, ctx, state)
    }

    private suspend fun executeAndRecord(
        parsed: ParsedToolCall,
        writer: BufferedWriter,
        toolExecutor: CliToolExecutor,
        ctx: InteractiveSessionContext,
        state: LoopState
    ) {
        val startTime = System.currentTimeMillis()
        val result = toolExecutor.execute(parsed)
        val durationMs = System.currentTimeMillis() - startTime
        val success = result.contains("\"success\":true")

        val entry = ToolCallLogEntry(parsed.name, durationMs, success, result.length)
        ctx.recordToolCall(entry)
        state.recordToolCall(success)
        sendToStdin(writer, result)
    }

    private suspend fun handleToolCallLimitExceeded(name: String, writer: BufferedWriter) {
        logger.warn("Tool call limit exceeded for '{}'", name)
        val errorResult = ToolCallProtocol.formatToolResult(
            name = name, success = false, data = "", error = TOOL_LIMIT_MSG
        )
        sendToStdin(writer, errorResult)
    }
}

/**
 * Mutable accumulator for loop metrics and document content.
 * Converted to an immutable [LoopResult] at loop exit.
 */
private class LoopState {
    private val documentLines = mutableListOf<String>()
    var toolCallsExecuted = 0; private set
    var toolCallsFailed = 0; private set

    fun appendLine(line: String) { documentLines.add(line) }

    fun recordToolCall(success: Boolean) {
        toolCallsExecuted++
        if (!success) toolCallsFailed++
    }

    fun toLoopResult(timedOut: Boolean) = LoopResult(
        document = documentLines.joinToString("\n"),
        timedOut = timedOut,
        toolCallsExecuted = toolCallsExecuted,
        toolCallsFailed = toolCallsFailed
    )
}
