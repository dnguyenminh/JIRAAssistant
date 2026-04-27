package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.agent.ba.subprocess.pipeline.CliInteractiveStrategy
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.InteractiveSessionContext
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.*

/**
 * Integration tests for the CLI interactive pipeline end-to-end flow.
 *
 * Uses PipedInputStream/PipedOutputStream to simulate a CLI process
 * without spawning a real one.
 *
 * _Requirements: 1.7, 6.2, 9.4, 10.1_
 */
@Tag("cli-interactive-ba-agent")
class CliInteractiveIntegrationTest {

    // -- Mock SubprocessProxy --

    private fun mockProxy(): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(
            id = request.id, success = true,
            data = """{"key":"${request.name}"}""", error = ""
        )
        override fun getAvailableToolDescriptors(): List<ToolDescriptor> =
            emptyList()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    // -- Test 1: Full CLI interactive loop with mock process --

    @Test
    fun `full CLI interactive loop with mock process`() = runBlocking(Dispatchers.IO) {
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut, 1 shl 16)
        val sinkOut = PipedOutputStream()
        val sinkIn = PipedInputStream(sinkOut, 1 shl 16)

        val reader = BufferedReader(InputStreamReader(pipeIn))
        val writer = BufferedWriter(OutputStreamWriter(sinkOut))
        val engine = CliInteractiveEngine()
        val executor = CliToolExecutor(mockProxy())
        val ctx = InteractiveSessionContext()
        val config = LoopConfig(maxToolCalls = 50, timeoutSeconds = 10)

        val sinkJob = launchSinkDrain(sinkIn)
        val writerJob = launchFeedLines(pipeOut)
        val result = async { engine.runInteractiveLoop(reader, writer, executor, ctx, config) }

        val loopResult = result.await()
        writerJob.join(); writer.close(); sinkJob.join()

        assertFalse(loopResult.timedOut, "Should not time out")
        assertEquals(2, loopResult.toolCallsExecuted, "Two tool calls expected")
        assertEquals(0, loopResult.toolCallsFailed, "No failures expected")
        assertTrue(loopResult.document.contains("Line one"), "Doc should contain 'Line one'")
        assertTrue(loopResult.document.contains("Line two"), "Doc should contain 'Line two'")
    }

    private fun kotlinx.coroutines.CoroutineScope.launchSinkDrain(
        sinkIn: PipedInputStream
    ) = launch(Dispatchers.IO) {
        BufferedReader(InputStreamReader(sinkIn)).use { sr ->
            try { while (sr.readLine() != null) { /* drain */ } } catch (_: IOException) {}
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchFeedLines(
        pipeOut: PipedOutputStream
    ) = launch(Dispatchers.IO) {
        BufferedWriter(OutputStreamWriter(pipeOut)).use { pw ->
            pw.writeLn("Line one")
            pw.writeLn("""{"toolCall":{"name":"tool_a","arguments":{"k":"v"}}}""")
            pw.writeLn("Line two")
            pw.writeLn("""{"toolCall":{"name":"tool_b","arguments":{}}}""")
            pw.writeLn("---END---")
        }
    }

    // -- Test 2: Process termination on completion (already-dead process) --

    @Test
    fun `terminateProcess does not throw on already-dead process`() {
        val process = ProcessBuilder(windowsSafeEchoCommand())
            .redirectErrorStream(true).start()
        process.waitFor()
        assertFalse(process.isAlive, "Process should be dead before terminate")
        assertDoesNotThrow { CliInteractiveEngine().terminateProcess(process) }
    }

    // -- Test 3: Process termination on timeout --

    @Test
    fun `interactive loop returns timedOut when END delimiter missing`() =
        runBlocking(Dispatchers.IO) {
            val pipeOut = PipedOutputStream()
            val pipeIn = PipedInputStream(pipeOut, 1 shl 16)
            val sinkOut = PipedOutputStream()
            val sinkIn = PipedInputStream(sinkOut, 1 shl 16)

            val reader = BufferedReader(InputStreamReader(pipeIn))
            val writer = BufferedWriter(OutputStreamWriter(sinkOut))
            val engine = CliInteractiveEngine()
            val executor = CliToolExecutor(mockProxy())
            val ctx = InteractiveSessionContext()
            val config = LoopConfig(maxToolCalls = 50, timeoutSeconds = 2)

            val sinkJob = launchSinkDrain(sinkIn)
            val writerJob = launchSlowFeedWithoutEnd(pipeOut)
            val result = async { engine.runInteractiveLoop(reader, writer, executor, ctx, config) }

            val loopResult = result.await()
            writerJob.cancel(); pipeOut.close(); writer.close(); sinkJob.join()

            assertTrue(loopResult.timedOut, "Loop should time out")
            assertTrue(loopResult.document.contains("partial"), "Partial content expected")
        }

    private fun kotlinx.coroutines.CoroutineScope.launchSlowFeedWithoutEnd(
        pipeOut: PipedOutputStream
    ) = launch(Dispatchers.IO) {
        try {
            BufferedWriter(OutputStreamWriter(pipeOut)).use { pw ->
                pw.writeLn("partial content here")
                pw.flush()
                // Keep stream open — no ---END---, no close
                kotlinx.coroutines.delay(10_000)
            }
        } catch (_: Exception) { /* cancelled or broken pipe */ }
    }

    // -- Test 4: Drop-in replacement in BASubprocessOrchestrator --

    @Test
    fun `createDefaultStrategy returns CliInteractiveStrategy`() {
        val proxy = mockProxy()
        val settings = stubSettingsRepository()
        val strategy = BASubprocessOrchestrator.createDefaultStrategy(
            proxy, settings, null
        )
        assertTrue(
            strategy is CliInteractiveStrategy,
            "Default strategy should be CliInteractiveStrategy"
        )
    }

    // -- Helpers --

    private fun BufferedWriter.writeLn(text: String) {
        write(text); newLine(); flush()
    }

    private fun windowsSafeEchoCommand(): List<String> {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) listOf("cmd", "/c", "echo", "test")
        else listOf("echo", "test")
    }

    private fun stubSettingsRepository() = object :
        com.assistant.settings.SettingsRepository {
        override suspend fun getAll() = emptyMap<String, String>()
        override suspend fun get(key: String): String? = null
        override suspend fun put(key: String, value: String) {}
        override suspend fun putAll(settings: Map<String, String>) {}
    }
}
