package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

/**
 * Helper functions for BaseNodeCliClient process execution.
 * Extracted to keep BaseNodeCliClient within the 200-line limit.
 */

/** Execute a stateless CLI process: write stdin, close, read stdout, wait for exit. */
internal fun executeStatelessProcess(
    command: List<String>, prompt: String, displayName: String,
    timeoutSeconds: Long, json: Json, logger: Logger
): AiCliResponse {
    logger.debug("Command: {}", command.joinToString(" "))
    val process = buildCliProcess(command)
    writeStdinAndClose(process, prompt, logger)

    val stdoutBuf = StringBuilder()
    val stderrBuf = StringBuilder()
    val stdoutThread = startReader(process.inputStream, stdoutBuf, logger, "STDOUT")
    val stderrThread = startReader(process.errorStream, stderrBuf, logger, "STDERR")

    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly(); stdoutThread.interrupt(); stderrThread.interrupt()
        throw RuntimeException("$displayName timed out after $timeoutSeconds seconds")
    }
    stdoutThread.join(5000); stderrThread.join(5000)

    val output = stdoutBuf.toString().trim()
    val errorOutput = stderrBuf.toString().trim()
    val exitCode = process.exitValue()
    logger.debug("Process exit code: {}, stdout: {} chars", exitCode, output.length)

    if (exitCode != 0) throw RuntimeException("$displayName failed (exit $exitCode): $errorOutput")
    if (output.isEmpty()) throw RuntimeException("$displayName returned empty output. Stderr: $errorOutput")

    logger.info("{} response received [STATELESS]", displayName)
    return parseJsonOrPlainText(output, json, logger)
}

/** Execute a persistent CLI process: write stdin, read NDJSON stream until "type":"result". */
internal fun executeStreamProcess(
    command: List<String>, prompt: String, displayName: String,
    currentSessionId: String?, timeoutSeconds: Long, json: Json, logger: Logger
): AiCliResponse {
    logger.info("[STREAM-DEBUG] Command: {}", command.joinToString(" "))
    val process = buildCliProcess(command)
    writeStdinAndClose(process, prompt, logger)

    // Read stderr in background thread
    val stderrBuf = StringBuilder()
    val stderrThread = try {
        startReader(process.errorStream, stderrBuf, logger, "STREAM-STDERR")
            .also { it.isDaemon = true }
    } catch (e: Exception) {
        logger.error("[STREAM-DEBUG] Failed to start stderr reader: {}", e.message)
        null
    }

    // Read stdout stream events on main thread (blocking)
    logger.info("[STREAM-DEBUG] Starting stream read, process alive: {}", process.isAlive)
    val result = try {
        readStreamEvents(process, json, logger)
    } catch (e: Exception) {
        logger.error("[STREAM-DEBUG] Error in readStreamEvents: {} - {}", e.javaClass.simpleName, e.message)
        StreamResult("", null, false)
    }
    logger.info("[STREAM-DEBUG] Stream read done: content={} chars, resultReceived={}", result.content.length, result.resultReceived)

    // Wait for process to finish
    process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (process.isAlive) process.destroyForcibly()
    stderrThread?.join(5000)

    val errorOutput = stderrBuf.toString().trim()
    val exitCode = if (process.isAlive) -1 else process.exitValue()
    logger.info("[STREAM-DEBUG] Process exit code: {}, stderr: {} chars", exitCode, errorOutput.length)

    if (result.content.isBlank() && !result.resultReceived) {
        throw RuntimeException(
            "$displayName returned empty response in persistent mode. " +
                "Exit code: $exitCode. Stderr: ${errorOutput.take(500)}"
        )
    }
    logger.info("{} response received [PERSISTENT]", displayName)
    return AiCliResponse(response = result.content.trim(), sessionId = result.sessionId ?: currentSessionId)
}

// ==================== PROCESS BUILDING ====================

private fun buildCliProcess(command: List<String>): Process {
    val pb = ProcessBuilder(command).also { it.redirectErrorStream(false) }
    pb.environment()["PYTHONIOENCODING"] = "utf-8"
    pb.environment()["NODE_OPTIONS"] = "--no-warnings"
    return pb.start()
}

private fun writeStdinAndClose(process: Process, prompt: String, logger: Logger) {
    process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(prompt); it.flush() }
    logger.debug("Prompt written, stdin closed")
}

// ==================== OUTPUT READING ====================

private fun startReader(
    stream: java.io.InputStream, buffer: StringBuilder, logger: Logger, label: String
): Thread = Thread {
    try {
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.forEachLine { line -> buffer.appendLine(line); logger.debug("[{}] {}", label, line) }
        }
    } catch (e: Exception) {
        logger.error("Error reading {}: {}", label, e.message)
    }
}.also { it.start() }

private fun parseJsonOrPlainText(output: String, json: Json, logger: Logger): AiCliResponse {
    return try {
        val obj = json.parseToJsonElement(output).jsonObject
        AiCliResponse(
            response = obj["response"]?.jsonPrimitive?.content ?: "",
            sessionId = obj["session_id"]?.jsonPrimitive?.content,
            rawJson = output
        )
    } catch (_: Exception) {
        logger.debug("Response is not JSON, treating as plain text")
        AiCliResponse(response = output.trim())
    }
}

// ==================== STREAM EVENT READING ====================

internal data class StreamResult(val content: String, val sessionId: String?, val resultReceived: Boolean)

private sealed class StreamEvent {
    data class Init(val sessionId: String?) : StreamEvent()
    data class Message(val content: String) : StreamEvent()
    data object Result : StreamEvent()
    data object Unknown : StreamEvent()
}

private fun readStreamEvents(process: Process, json: Json, logger: Logger): StreamResult {
    var sessionId: String? = null
    val content = StringBuilder()
    var resultReceived = false
    try {
        val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
        logger.debug("Starting stream read, process alive: {}", process.isAlive)
        var line: String? = reader.readLine()
        logger.debug("First readLine returned: {}", line?.take(100) ?: "null")
        while (line != null) {
            logger.debug("[STREAM] {}", line)
            if (line.isNotBlank()) {
                when (val evt = parseStreamEvent(line, json, logger)) {
                    is StreamEvent.Init -> sessionId = evt.sessionId
                    is StreamEvent.Message -> content.append(evt.content)
                    is StreamEvent.Result -> {
                        resultReceived = true
                        logger.debug("Result event received - response complete")
                        break
                    }
                    is StreamEvent.Unknown -> { /* skip */ }
                }
            }
            line = reader.readLine()
        }
        logger.debug("Stream read loop ended, resultReceived={}", resultReceived)
    } catch (e: Exception) {
        logger.error("Error reading stream: {} - {}", e.javaClass.simpleName, e.message)
    }
    return StreamResult(content.toString(), sessionId, resultReceived)
}

private fun parseStreamEvent(line: String, json: Json, logger: Logger): StreamEvent {
    return try {
        val event = json.parseToJsonElement(line).jsonObject
        when (event["type"]?.jsonPrimitive?.content) {
            "init" -> StreamEvent.Init(event["session_id"]?.jsonPrimitive?.content)
            "message" -> {
                val role = event["role"]?.jsonPrimitive?.content
                val text = event["content"]?.jsonPrimitive?.content
                if (role == "assistant" && text != null) StreamEvent.Message(text) else StreamEvent.Unknown
            }
            "result" -> StreamEvent.Result
            else -> StreamEvent.Unknown
        }
    } catch (_: Exception) {
        logger.debug("Non-JSON stream line: {}", line)
        StreamEvent.Unknown
    }
}
