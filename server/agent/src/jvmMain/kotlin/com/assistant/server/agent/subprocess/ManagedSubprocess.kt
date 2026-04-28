package com.assistant.server.agent.subprocess

import kotlinx.coroutines.sync.Mutex
import java.io.BufferedReader
import java.io.BufferedWriter

/**
 * State holder for a single running agent subprocess.
 *
 * Wraps a JVM [Process] with convenience accessors for
 * stdin/stdout/stderr streams and concurrency controls.
 *
 * The [commandMutex] ensures only one command is sent to
 * the subprocess at a time, preventing interleaved I/O on
 * the stdin/stdout channel (Requirement 13.3).
 *
 * The [lastActivityTimestamp] tracks when the last command
 * was sent, enabling unresponsive-process detection.
 *
 * The [restartCount] tracks how many times this subprocess
 * has been restarted after crashes, useful for diagnostics
 * and circuit-breaking.
 *
 * @property agentType Unique identifier for the agent type
 *   (e.g., "ba-agent", "qa-agent")
 * @property process The underlying JVM process
 * @property stdin Writer connected to the process's stdin
 * @property stdout Reader connected to the process's stdout
 * @property stderr Reader connected to the process's stderr
 * @property commandMutex Mutex ensuring sequential command
 *   execution per subprocess
 * @property lastActivityTimestamp Epoch millis of the last
 *   command sent to this subprocess
 * @property restartCount Number of times this subprocess has
 *   been restarted after a crash
 */
class ManagedSubprocess(
    val agentType: String,
    val process: Process,
    val stdin: BufferedWriter,
    val stdout: BufferedReader,
    val stderr: BufferedReader,
    val commandMutex: Mutex = Mutex(),
    var lastActivityTimestamp: Long = System.currentTimeMillis(),
    var restartCount: Int = 0
) {

    /**
     * Returns `true` if the underlying process is still alive.
     */
    fun isAlive(): Boolean = process.isAlive

    /**
     * Updates [lastActivityTimestamp] to the current time.
     */
    fun touch() {
        lastActivityTimestamp = System.currentTimeMillis()
    }
}
