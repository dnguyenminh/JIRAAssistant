package com.assistant.server.agent.subprocess

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Unit tests for the fixed [emitStdoutUntilDelimiter] function.
 *
 * Validates: bugfix requirements 2.1–2.4, preservation 3.1, 3.3
 */
@Tag("stdout-blocking-timeout-fix")
class EmitStdoutInterruptibleTest {

    // ── 3.2 Blocking stream terminates within idle timeout ──

    @Test
    fun `blocking stream breaks on idle timeout`() = runBlocking {
        val (managed, pipeOut) = buildPipedManaged()
        // Write some lines but never delimiter or EOF
        pipeOut.write("line1\n".toByteArray())
        pipeOut.write("line2\n".toByteArray())
        pipeOut.flush()

        val collected = mutableListOf<String>()
        val collector = FlowCollector<String> { collected.add(it) }
        val idleMs = 1_000L

        val start = System.currentTimeMillis()
        emitStdoutUntilDelimiter(managed, collector, idleMs)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf("line1", "line2"), collected)
        assertTrue(elapsed < idleMs + 3_000,
            "Should terminate within idle timeout + tolerance, took ${elapsed}ms")
        pipeOut.close()
    }

    // ── 3.3 Delimiter-terminated stream returns all lines ───

    @Test
    fun `delimiter stream returns all lines`() = runBlocking {
        val content = "alpha\nbeta\ngamma\n---END---\nignored\n"
        val managed = buildStringManaged(content)

        val collected = mutableListOf<String>()
        val collector = FlowCollector<String> { collected.add(it) }

        val start = System.currentTimeMillis()
        emitStdoutUntilDelimiter(managed, collector)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf("alpha", "beta", "gamma"), collected)
        assertTrue(elapsed < 2_000, "Should return promptly, took ${elapsed}ms")
    }

    // ── 3.4 EOF-terminated stream returns all lines ─────────

    @Test
    fun `EOF stream returns all lines`() = runBlocking {
        val content = "first\nsecond\nthird\n"
        val managed = buildStringManaged(content)

        val collected = mutableListOf<String>()
        val collector = FlowCollector<String> { collected.add(it) }

        emitStdoutUntilDelimiter(managed, collector)

        assertEquals(listOf("first", "second", "third"), collected)
    }

    // ── 3.5 Parent cancellation via idle timeout on blocked stream ─

    @Test
    fun `idle timeout breaks blocked read within tolerance`() = runBlocking {
        val (managed, pipeOut) = buildPipedManaged()
        // Write one line then leave stream open — readLine blocks
        pipeOut.write("only-line\n".toByteArray())
        pipeOut.flush()

        val collected = mutableListOf<String>()
        val collector = FlowCollector<String> { collected.add(it) }
        val idleMs = 2_000L

        val start = System.currentTimeMillis()
        emitStdoutUntilDelimiter(managed, collector, idleMs)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf("only-line"), collected)
        // Should terminate around idleMs, not hang forever
        assertTrue(elapsed >= idleMs - 200,
            "Should wait at least ~idleMs, took ${elapsed}ms")
        assertTrue(elapsed < idleMs + 5_000,
            "Should terminate within tolerance, took ${elapsed}ms")
        pipeOut.close()
    }

    // ── 3.6 Preservation: default idleTimeoutMs = MAX_VALUE ─

    @Test
    fun `default idle timeout is MAX_VALUE (preservation)`() = runBlocking {
        // With delimiter present, function returns immediately
        // regardless of timeout — proves default doesn't interfere
        val content = "preserved\n---END---\n"
        val managed = buildStringManaged(content)

        val collected = mutableListOf<String>()
        val collector = FlowCollector<String> { collected.add(it) }

        val start = System.currentTimeMillis()
        // Call without idleTimeoutMs — uses default Long.MAX_VALUE
        emitStdoutUntilDelimiter(managed, collector)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf("preserved"), collected)
        assertTrue(elapsed < 2_000, "Default timeout should not delay")
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun buildPipedManaged(): Pair<ManagedSubprocess, PipedOutputStream> {
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut)
        val process = FakeAliveProcess()
        val managed = ManagedSubprocess(
            agentType = "test-pipe",
            process = process,
            stdin = BufferedWriter(System.out.writer()),
            stdout = BufferedReader(pipeIn.reader()),
            stderr = BufferedReader("".reader())
        )
        return managed to pipeOut
    }

    private fun buildStringManaged(content: String): ManagedSubprocess {
        val process = FakeAliveProcess()
        return ManagedSubprocess(
            agentType = "test-string",
            process = process,
            stdin = BufferedWriter(System.out.writer()),
            stdout = BufferedReader(content.reader()),
            stderr = BufferedReader("".reader())
        )
    }
}
