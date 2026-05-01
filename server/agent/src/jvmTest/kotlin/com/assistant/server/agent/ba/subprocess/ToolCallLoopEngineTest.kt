package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.SubprocessMessage
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.models.ToolDescriptor
import com.assistant.config.JsonConfig
import com.assistant.server.agent.subprocess.MessageProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unit tests for ToolCallLoopEngine.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.6, 3.7, 3.8
 */
@Tag("agent-subprocess-orchestration")
class ToolCallLoopEngineTest {

    private val json = JsonConfig.instance

    @Test
    fun `delimiter ends loop and returns document`() = runBlocking {
        val engine = ToolCallLoopEngine(stubProxy(), UnitTestNoOpReporter)
        val result = engine.runLoop(
            stdoutFlow = flowOf("Hello", "World", MessageProtocol.DELIMITER),
            stdinWriter = { }
        )

        assertEquals("Hello\nWorld", result.document)
        assertFalse(result.timedOut)
        assertEquals(0, result.toolCallsExecuted)
    }

    @Test
    fun `timeout returns partial document`() = runBlocking {
        val slowFlow = flow {
            emit("Line 1")
            delay(3000) // exceeds 1s timeout
            emit("Line 2")
            emit(MessageProtocol.DELIMITER)
        }
        val engine = ToolCallLoopEngine(stubProxy(), UnitTestNoOpReporter)

        val result = engine.runLoop(
            stdoutFlow = slowFlow,
            stdinWriter = { },
            timeoutSeconds = 1
        )

        assertTrue(result.timedOut)
        assertTrue(result.document.contains("Line 1"))
    }

    @Test
    fun `progress reported per tool call`() = runBlocking {
        val reported = CopyOnWriteArrayList<String>()
        val reporter = object : ProgressReporter {
            override suspend fun reportPhase(
                phaseName: String, phaseIndex: Int, totalPhases: Int
            ) = Unit
            override suspend fun reportProgress(
                percent: Int, message: String
            ) { reported.add("$percent:$message") }
            override suspend fun reportToolCall(
                toolName: String, status: String
            ) { reported.add("tool:$toolName:$status") }
        }
        val req = ToolCallRequest("id1", "myTool")
        val line = formatToolCallLine(req)
        val engine = ToolCallLoopEngine(stubProxy(), reporter)

        engine.runLoop(
            stdoutFlow = flowOf(line, MessageProtocol.DELIMITER),
            stdinWriter = { }
        )

        assertTrue(
            reported.any { it.contains("myTool") },
            "Progress should report tool name"
        )
    }

    @Test
    fun `error responses forwarded to subprocess`() = runBlocking {
        val errorProxy = object : SubprocessProxy {
            override suspend fun handleToolCallRequest(
                request: ToolCallRequest
            ) = ToolCallResponse(
                id = request.id, success = false,
                error = "Tool not found"
            )
            override fun getAvailableToolDescriptors() =
                emptyList<ToolDescriptor>()
            override fun buildToolListMessage() = ""
            override fun buildToolsUpdatedMessage() = ""
        }
        val written = CopyOnWriteArrayList<String>()
        val req = ToolCallRequest("err1", "missingTool")
        val line = formatToolCallLine(req)
        val engine = ToolCallLoopEngine(errorProxy, UnitTestNoOpReporter)

        val result = engine.runLoop(
            stdoutFlow = flowOf(line, MessageProtocol.DELIMITER),
            stdinWriter = { written.add(it) }
        )

        assertEquals(1, written.size)
        assertEquals(1, result.toolCallsFailed)
        assertEquals(1, result.toolCallsExecuted)
        // Verify the error response was sent back
        val respMsg = json.decodeFromString(
            SubprocessMessage.serializer(),
            written[0].lines().first { it.trim().startsWith("{") }
        )
        assertFalse(respMsg.toolResult!!.success)
        assertEquals("err1", respMsg.toolResult!!.id)
    }

    @Test
    fun `empty stdout returns empty document`() = runBlocking {
        val engine = ToolCallLoopEngine(stubProxy(), UnitTestNoOpReporter)
        val result = engine.runLoop(
            stdoutFlow = flowOf(MessageProtocol.DELIMITER),
            stdinWriter = { }
        )

        assertEquals("", result.document)
        assertEquals(0, result.toolCallsExecuted)
        assertFalse(result.timedOut)
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun formatToolCallLine(req: ToolCallRequest): String {
        val msg = SubprocessMessage(type = "toolCall", toolCall = req)
        return json.encodeToString(
            SubprocessMessage.serializer(), msg
        )
    }

    private fun stubProxy() = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(
            id = request.id, success = true, data = "ok"
        )
        override fun getAvailableToolDescriptors() =
            emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }
}

/** No-op ProgressReporter for unit tests. */
private object UnitTestNoOpReporter : ProgressReporter {
    override suspend fun reportPhase(
        phaseName: String, phaseIndex: Int, totalPhases: Int
    ) = Unit
    override suspend fun reportProgress(
        percent: Int, message: String
    ) = Unit
    override suspend fun reportToolCall(
        toolName: String, status: String
    ) = Unit
}
