package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.config.JsonConfig
import com.assistant.server.agent.generators.toolCallRequest
import com.assistant.server.agent.generators.toolCallResponse
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for ToolCallRequest / ToolCallResponse
 * serialization (Property 37).
 */
@OptIn(ExperimentalKotest::class)
class SubprocessProxyPropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 37: ToolCallRequest serialization round-trip.
     *
     * For any valid ToolCallRequest with arbitrary id, name, and
     * arguments, serializing to JSON then deserializing back produces
     * an equivalent ToolCallRequest.
     *
     * **Validates: Requirements 20.1, 20.4**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-37")
    fun `ToolCallRequest serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.toolCallRequest()) { original ->
                val serialized = json.encodeToString(
                    ToolCallRequest.serializer(), original
                )
                val restored = json.decodeFromString(
                    ToolCallRequest.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }

    /**
     * Property 37: ToolCallResponse serialization round-trip.
     *
     * For any valid ToolCallResponse with arbitrary id, success, data,
     * and error, serializing to JSON then deserializing back produces
     * an equivalent ToolCallResponse.
     *
     * **Validates: Requirements 20.1, 20.4**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-37")
    fun `ToolCallResponse serialization round-trip`() {
        runBlocking {
            checkAll(cfg, Arb.toolCallResponse()) { original ->
                val serialized = json.encodeToString(
                    ToolCallResponse.serializer(), original
                )
                val restored = json.decodeFromString(
                    ToolCallResponse.serializer(), serialized
                )
                restored shouldBe original
            }
        }
    }
}
