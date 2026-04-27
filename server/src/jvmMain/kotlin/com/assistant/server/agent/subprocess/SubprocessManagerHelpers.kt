package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.SubprocessConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InterruptedIOException

/**
 * Internal helper functions for [SubprocessManagerImpl].
 *
 * Split from the main class to respect the 200-line file limit.
 * Handles process spawning, stdin/stdout I/O, and stderr capture.
 *
 * Requirements: 13.1, 13.2, 13.5, 13.6
 */

/**
 * Spawns a new subprocess for the given agent type.
 *
 * Uses [ProcessBuilder] with `redirectErrorStream(false)` to keep
 * stderr separate from stdout. Launches a dedicated coroutine to
 * consume stderr and log it as warnings.
 *
 * Requirements: 13.1, 13.6
 */
internal suspend fun SubprocessManagerImpl.spawnSubprocess(
    agentType: String,
    previousRestartCount: Int
): ManagedSubprocess {
    val config = configs[agentType]
        ?: throw IllegalArgumentException("No SubprocessConfig for agent type '$agentType'")

    val process = startProcess(config)
    val managed = ManagedSubprocess(
        agentType = agentType,
        process = process,
        stdin = BufferedWriter(process.outputStream.writer()),
        stdout = BufferedReader(process.inputStream.reader()),
        stderr = BufferedReader(process.errorStream.reader()),
        restartCount = previousRestartCount + if (previousRestartCount > 0 || subprocesses.containsKey(agentType)) 1 else 0
    )

    launchStderrCapture(managed)
    subprocesses[agentType] = managed
    logger.info("Spawned subprocess for '{}' (pid={}, restarts={})", agentType, process.pid(), managed.restartCount)
    return managed
}

/**
 * Starts an OS process from [SubprocessConfig] via [ProcessBuilder].
 *
 * Keeps stderr separate (`redirectErrorStream(false)`) so that
 * error output doesn't mix with response data.
 */
private fun startProcess(config: SubprocessConfig): Process {
    val command = buildCommand(config)
    val pb = ProcessBuilder(command)
    pb.redirectErrorStream(false)
    config.environment.forEach { (k, v) -> pb.environment()[k] = v }
    pb.directory(File(config.workingDirectory))
    return pb.start()
}

/**
 * Builds the command list, handling Windows .cmd/.bat scripts.
 */
private fun buildCommand(config: SubprocessConfig): List<String> {
    val isWindows = System.getProperty("os.name")
        .lowercase().contains("win")
    val cmdLower = config.cliCommand.lowercase()
    return if (isWindows && (cmdLower.endsWith(".cmd") || cmdLower.endsWith(".bat")
                || cmdLower == "npx" || cmdLower == "uvx" || cmdLower == "node")
    ) {
        listOf("cmd.exe", "/c", config.cliCommand) + config.cliArgs
    } else {
        listOf(config.cliCommand) + config.cliArgs
    }
}

/**
 * Launches a dedicated coroutine on [Dispatchers.IO] to consume
 * stderr output and log each line as a warning.
 *
 * Requirements: 13.6
 */
private fun SubprocessManagerImpl.launchStderrCapture(managed: ManagedSubprocess) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
        try {
            managed.stderr.lineSequence().forEach { line ->
                logger.warn("[{}] stderr: {}", managed.agentType, line)
            }
        } catch (_: Exception) {
            // Process terminated — stderr stream closed
        }
    }
}

/**
 * Writes a command to the subprocess's stdin.
 *
 * When [isRealCli] is `true`, writes plain text directly — real CLI tools
 * (Gemini CLI, Copilot CLI) expect unframed input, not JSON.
 * When `false`, uses [MessageProtocol.formatCommand] for JSON framing.
 *
 * Requirements: 13.2, bugfix 2.1–2.3
 */
internal fun writeCommandToStdin(
    managed: ManagedSubprocess,
    command: String,
    isRealCli: Boolean = false
) {
    val output = if (isRealCli) command + "\n" else MessageProtocol.formatCommand(command)
    managed.stdin.write(output)
    managed.stdin.flush()
}

/**
 * Reads stdout lines and emits them via the [FlowCollector] until
 * the [MessageProtocol] delimiter is encountered.
 *
 * Uses a polling approach with [BufferedReader.ready] to make the
 * blocking [readLine] cancellable by idle timeout. On Windows,
 * `Thread.interrupt()` does NOT interrupt `InputStream.read()` on
 * process pipes, so `runInterruptible` alone cannot break the read.
 *
 * The polling loop checks `ready()` every [POLL_INTERVAL_MS] ms.
 * If no data arrives within [idleTimeoutMs], the loop breaks.
 *
 * Requirements: 13.2, 13.5, bugfix 2.1–2.4
 */
internal suspend fun emitStdoutUntilDelimiter(
    managed: ManagedSubprocess,
    collector: FlowCollector<String>,
    idleTimeoutMs: Long = Long.MAX_VALUE
) {
    val log = LoggerFactory.getLogger("EmitStdout")
    var deadline = if (idleTimeoutMs < Long.MAX_VALUE)
        System.currentTimeMillis() + idleTimeoutMs else Long.MAX_VALUE
    while (true) {
        val line: String?
        try {
            line = pollReadLine(managed.stdout, deadline)
        } catch (_: CancellationException) {
            log.info("[{}] Read loop cancelled", managed.agentType)
            throw CancellationException("Read loop cancelled")
        } catch (_: InterruptedIOException) {
            log.warn("[{}] readLine() interrupted", managed.agentType)
            break
        }
        if (line == null) {
            if (idleTimeoutMs < Long.MAX_VALUE) {
                log.warn("[{}] Idle timeout ({}ms) — breaking",
                    managed.agentType, idleTimeoutMs)
            }
            break
        }
        if (MessageProtocol.isDelimiter(line)) break
        collector.emit(line)
        // Reset deadline on each successful line
        if (idleTimeoutMs < Long.MAX_VALUE) {
            deadline = System.currentTimeMillis() + idleTimeoutMs
        }
    }
}

private const val POLL_INTERVAL_MS = 200L

/**
 * Polls [BufferedReader.ready] until data is available, then reads
 * a line. Returns `null` if deadline is exceeded or stream ends.
 */
private suspend fun pollReadLine(
    reader: BufferedReader, deadline: Long
): String? {
    while (true) {
        currentCoroutineContext().ensureActive()
        val ready = withContext(Dispatchers.IO) { reader.ready() }
        if (ready) {
            return withContext(Dispatchers.IO) { reader.readLine() }
        }
        if (System.currentTimeMillis() >= deadline) return null
        delay(POLL_INTERVAL_MS)
    }
}
