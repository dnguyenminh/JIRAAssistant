package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.SubprocessConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Integration tests for the full sendCommand → emitStdoutUntilDelimiter
 * pipeline with timeout behavior.
 *
 * Validates: bugfix requirements 2.1–2.4, preservation 3.1, 3.6
 */
@Tag("stdout-blocking-timeout-fix")
class SubprocessTimeoutIntegrationTest {

    // ── 4.2 Blocking subprocess terminates on cancellation ──

    @Test
    fun `sendCommand flow terminates when collected with timeout`() = runBlocking {
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut)
        val config = SubprocessConfig(
            agentType = "blocking-cli",
            cliCommand = "fake",
            isRealCli = true,
            unresponsiveTimeoutMs = 2_000L
        )

        val manager = SubprocessManagerImpl(mapOf("blocking-cli" to config))
        val process = FakeAliveProcess()
        val managed = ManagedSubprocess(
            agentType = "blocking-cli",
            process = process,
            stdin = BufferedWriter(pipeOut.writer()),
            stdout = BufferedReader(pipeIn.reader()),
            stderr = BufferedReader("".reader())
        )
        manager.subprocesses["blocking-cli"] = managed

        // Write some lines but never delimiter — stream blocks
        val writerPipeOut = PipedOutputStream()
        val writerPipeIn = PipedInputStream(writerPipeOut)
        val managed2 = ManagedSubprocess(
            agentType = "blocking-cli",
            process = process,
            stdin = BufferedWriter(System.out.writer()),
            stdout = BufferedReader(writerPipeIn.reader()),
            stderr = BufferedReader("".reader())
        )
        manager.subprocesses["blocking-cli"] = managed2

        // Write some lines then leave stream open (no delimiter)
        writerPipeOut.write("response line 1\n".toByteArray())
        writerPipeOut.write("response line 2\n".toByteArray())
        writerPipeOut.flush()

        val start = System.currentTimeMillis()
        val flow = manager.sendCommand("blocking-cli", "test")
        val result = withTimeoutOrNull(8_000) { flow.toList() }
        val elapsed = System.currentTimeMillis() - start

        // Should terminate via idle timeout (2s) + some tolerance
        assertNotNull(result, "Flow should terminate, not hang")
        assertTrue(result!!.contains("response line 1"))
        assertTrue(result.contains("response line 2"))
        assertTrue(elapsed < 8_000, "Should finish well before 8s, took ${elapsed}ms")
        writerPipeOut.close()
    }

    // ── 4.3 Custom protocol preserves existing behavior ─────

    @Test
    fun `custom protocol subprocess completes normally`() = runBlocking {
        val content = "doc line 1\ndoc line 2\n---END---\n"
        val config = SubprocessConfig(
            agentType = "custom-proto",
            cliCommand = "fake",
            isRealCli = false
        )

        val manager = SubprocessManagerImpl(mapOf("custom-proto" to config))
        val process = FakeAliveProcess()
        val managed = ManagedSubprocess(
            agentType = "custom-proto",
            process = process,
            stdin = BufferedWriter(System.out.writer()),
            stdout = BufferedReader(content.reader()),
            stderr = BufferedReader("".reader())
        )
        manager.subprocesses["custom-proto"] = managed

        val flow = manager.sendCommand("custom-proto", "test")
        val lines = flow.toList()

        assertEquals(listOf("doc line 1", "doc line 2"), lines)
    }

    // ── 4.4 commandMutex released after timeout exit ────────

    @Test
    fun `mutex released after idle timeout exit`() = runBlocking {
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut)
        val config = SubprocessConfig(
            agentType = "mutex-test",
            cliCommand = "fake",
            isRealCli = true,
            unresponsiveTimeoutMs = 1_000L
        )

        val manager = SubprocessManagerImpl(mapOf("mutex-test" to config))
        val process = FakeAliveProcess()
        val managed = ManagedSubprocess(
            agentType = "mutex-test",
            process = process,
            stdin = BufferedWriter(System.out.writer()),
            stdout = BufferedReader(pipeIn.reader()),
            stderr = BufferedReader("".reader())
        )
        manager.subprocesses["mutex-test"] = managed

        // First command — will idle-timeout since no delimiter
        pipeOut.write("first\n".toByteArray())
        pipeOut.flush()

        val flow1 = manager.sendCommand("mutex-test", "cmd1")
        withTimeoutOrNull(5_000) { flow1.toList() }

        // Mutex should be released — verify by checking it's not locked
        val isLocked = managed.commandMutex.isLocked
        assertFalse(isLocked, "Mutex should be released after timeout")
        pipeOut.close()
    }
}
