package com.assistant.server.agent.subprocess

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.StringWriter

/**
 * Unit tests for [writeCommandToStdin] conditional behavior.
 *
 * Sub-task 6.1: isRealCli = true → plain text, no JSON, no ---END---
 * Sub-task 6.2: isRealCli = false → JSON-framed via MessageProtocol
 *
 * **Validates: Requirements 2.1, 3.1**
 */
@Tag("agent-subprocess-orchestration")
class WriteCommandToStdinTest {

    // ── 6.1 isRealCli = true ────────────────────────────

    @Test
    fun `real CLI writes plain text to stdin`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "Hello world", isRealCli = true)
        assertEquals("Hello world\n", captured.toString())
    }

    @Test
    fun `real CLI output has no JSON framing`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "analyze PROJ-1", isRealCli = true)
        val output = captured.toString()
        assertFalse(output.contains("{\"type\":\"command\""))
    }

    @Test
    fun `real CLI output has no END delimiter`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "analyze PROJ-1", isRealCli = true)
        val output = captured.toString()
        assertFalse(output.contains(MessageProtocol.DELIMITER))
    }

    // ── 6.2 isRealCli = false ───────────────────────────

    @Test
    fun `custom protocol writes JSON-framed output`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "analyze PROJ-1", isRealCli = false)
        val output = captured.toString()
        val expected = MessageProtocol.formatCommand("analyze PROJ-1")
        assertEquals(expected, output)
    }

    @Test
    fun `custom protocol output contains JSON type command`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "test cmd", isRealCli = false)
        val output = captured.toString()
        assertTrue(output.contains("{\"type\":\"command\""))
    }

    @Test
    fun `custom protocol output ends with delimiter`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "test cmd", isRealCli = false)
        val output = captured.toString()
        assertTrue(output.contains(MessageProtocol.DELIMITER))
    }

    @Test
    fun `default isRealCli is false`() {
        val (managed, captured) = buildManagedWithCapture()
        writeCommandToStdin(managed, "default test")
        val output = captured.toString()
        val expected = MessageProtocol.formatCommand("default test")
        assertEquals(expected, output)
    }

    // ── Helpers ─────────────────────────────────────────

    private fun buildManagedWithCapture(): Pair<ManagedSubprocess, StringWriter> {
        val sw = StringWriter()
        val process = FakeAliveProcess()
        val managed = ManagedSubprocess(
            agentType = "test-agent",
            process = process,
            stdin = BufferedWriter(sw),
            stdout = BufferedReader("".reader()),
            stderr = BufferedReader("".reader())
        )
        return managed to sw
    }
}
