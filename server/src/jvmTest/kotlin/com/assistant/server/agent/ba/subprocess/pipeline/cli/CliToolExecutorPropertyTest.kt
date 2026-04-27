package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.ParsedToolCall
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 5: Tool executor converts any response to valid protocol JSON.
 *
 * Generates random ToolCallResponse values and verifies that
 * CliToolExecutor.execute() always produces valid JSON matching
 * the {"toolResult":{...}} protocol format.
 *
 * **Validates: Requirements 4.2, 4.3, 9.2**
 */
@OptIn(ExperimentalKotest::class)
class CliToolExecutorPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)
    private val json = Json { ignoreUnknownKeys = true }

    // -- Generators --

    private fun arbToolName(): Arb<String> =
        Arb.string(1..30, Codepoint.alphanumeric())

    private fun arbArgMap(): Arb<Map<String, String>> =
        Arb.map(
            keyArb = Arb.string(1..15, Codepoint.alphanumeric()),
            valueArb = Arb.string(0..30, Codepoint.alphanumeric()),
            minSize = 0, maxSize = 5
        )

    private fun arbToolCallResponse(): Arb<ToolCallResponse> = arbitrary {
        ToolCallResponse(
            id = Arb.string(5..20, Codepoint.alphanumeric()).bind(),
            success = Arb.boolean().bind(),
            data = Arb.string(0..200).bind(),
            error = Arb.string(0..100).bind()
        )
    }

    // -- Helpers --

    /** Creates a SubprocessProxy that returns the given response. */
    private fun proxyReturning(
        response: ToolCallResponse
    ): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ): ToolCallResponse = response

        override fun getAvailableToolDescriptors(): List<ToolDescriptor> =
            emptyList()

        override fun buildToolListMessage(): String = ""
        override fun buildToolsUpdatedMessage(): String = ""
    }

    /** Creates a SubprocessProxy that throws the given exception. */
    private fun proxyThrowing(
        exception: Exception
    ): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ): ToolCallResponse = throw exception

        override fun getAvailableToolDescriptors(): List<ToolDescriptor> =
            emptyList()

        override fun buildToolListMessage(): String = ""
        override fun buildToolsUpdatedMessage(): String = ""
    }

    // -- Property 5a: Normal response → valid protocol JSON --

    /**
     * **Validates: Requirements 4.2, 4.3**
     *
     * For any ToolCallResponse, execute() produces valid JSON
     * with {"toolResult":{...}} format whose fields reflect
     * the original response.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P5a - any response converts to valid protocol JSON`() {
        runBlocking {
            checkAll(
                cfg, arbToolName(), arbArgMap(), arbToolCallResponse()
            ) { name, args, response ->
                val executor = CliToolExecutor(proxyReturning(response))
                val toolCall = ParsedToolCall(name, args)

                val result = executor.execute(toolCall)

                // Must be valid JSON
                val root = json.parseToJsonElement(result).jsonObject
                val tr = root["toolResult"]?.jsonObject

                assertNotNull(tr) { "Missing toolResult key in: $result" }
                assertEquals(name, tr!!["name"]?.jsonPrimitive?.content) {
                    "name mismatch"
                }
                assertEquals(
                    response.success,
                    tr["success"]?.jsonPrimitive?.boolean
                ) { "success mismatch" }
                assertEquals(
                    response.data,
                    tr["data"]?.jsonPrimitive?.content
                ) { "data mismatch" }
                assertEquals(
                    response.error,
                    tr["error"]?.jsonPrimitive?.content
                ) { "error mismatch" }
            }
        }
    }

    // -- Property 5b: Exception → success=false with error message --

    /**
     * **Validates: Requirements 4.3, 9.2**
     *
     * When SubprocessProxy throws an exception, execute() returns
     * valid JSON with success=false and the exception message in error.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P5b - exception produces success=false with error message`() {
        runBlocking {
            checkAll(
                cfg, arbToolName(), arbArgMap(),
                Arb.string(1..100, Codepoint.printableAscii())
            ) { name, args, errorMsg ->
                val executor = CliToolExecutor(
                    proxyThrowing(RuntimeException(errorMsg))
                )
                val toolCall = ParsedToolCall(name, args)

                val result = executor.execute(toolCall)

                val root = json.parseToJsonElement(result).jsonObject
                val tr = root["toolResult"]?.jsonObject

                assertNotNull(tr) { "Missing toolResult key in: $result" }
                assertEquals(name, tr!!["name"]?.jsonPrimitive?.content) {
                    "name mismatch on exception path"
                }
                assertFalse(tr["success"]!!.jsonPrimitive.boolean) {
                    "Expected success=false on exception"
                }
                assertEquals(
                    errorMsg,
                    tr["error"]?.jsonPrimitive?.content
                ) { "error message mismatch" }
                assertEquals(
                    "",
                    tr["data"]?.jsonPrimitive?.content
                ) { "data should be empty on exception" }
            }
        }
    }
}
