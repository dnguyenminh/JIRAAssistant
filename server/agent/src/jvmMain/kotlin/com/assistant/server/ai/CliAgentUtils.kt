package com.assistant.server.ai

import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

internal fun executeCliCommand(
    command: List<String>,
    input: String? = null,
    timeout: Long,
    logger: Logger
): ProcessResult {
    val isWindows = System.getProperty("os.name")
        .lowercase().contains("win")
    val actualCommand = if (isWindows) {
        listOf("cmd", "/c") + command
    } else {
        command
    }

    val pb = ProcessBuilder(actualCommand)
        .redirectErrorStream(false)
    val process = pb.start()

    if (input != null) {
        process.outputStream.bufferedWriter().use { it.write(input) }
    }

    val stdoutFuture = CompletableFuture.supplyAsync {
        process.inputStream.bufferedReader().readText()
    }
    val stderrFuture = CompletableFuture.supplyAsync {
        process.errorStream.bufferedReader().readText()
    }

    val completed = process.waitFor(
        timeout, TimeUnit.MILLISECONDS
    )
    return handleProcessCompletion(completed, process, stdoutFuture, stderrFuture, timeout, logger)
}

private fun handleProcessCompletion(
    completed: Boolean,
    process: Process,
    stdoutFuture: CompletableFuture<String>,
    stderrFuture: CompletableFuture<String>,
    timeout: Long,
    logger: Logger
): ProcessResult {
    if (!completed) {
        process.destroyForcibly()
        val partialOut = try { stdoutFuture.getNow("") } catch (_: Exception) { "" }
        val partialErr = try { stderrFuture.getNow("") } catch (_: Exception) { "" }
        logger.warn("CLI timeout: partial stdout=${partialOut.length} chars, stderr=${partialErr.take(500)}")
        return ProcessResult(1, partialOut, "Timeout after ${timeout}ms. stderr: ${partialErr.take(500)}")
    }
    return ProcessResult(process.exitValue(), stdoutFuture.get(), stderrFuture.get())
}
