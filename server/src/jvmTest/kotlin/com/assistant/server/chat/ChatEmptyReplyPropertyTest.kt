package com.assistant.server.chat

import com.assistant.server.chat.ChatResponseParser.EMPTY_REPLY_FALLBACK
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.az
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property-based tests for ChatResponseParser empty-reply bugfix.
 *
 * Property 1 (Bug Condition): Tool call JSON always yields
 * non-empty, human-readable reply — never raw JSON.
 *
 * Property 2 (Preservation): Valid JSON and plain text inputs
 * preserve original parse behavior unchanged.
 *
 * **Validates: Requirements 2.1, 2.2, 3.3, 3.4**
 */
@OptIn(ExperimentalKotest::class)
internal class ChatEmptyReplyPropertyTest {

    private val cfg = PropTestConfig(iterations = 25)

    private val arbName = Arb.string(1..30, Codepoint.az())

    /**
     * Property 1 — Bug Condition: tool call JSON → non-empty reply.
     *
     * For any random serverId/toolName, parsing mcpToolCall JSON
     * must return a non-blank reply that does not contain "mcpToolCall".
     *
     * **Validates: Requirements 2.2**
     */
    @Test
    fun `Property 1 - tool call JSON always yields non-empty reply without mcpToolCall`() {
        runBlocking {
            checkAll(cfg, arbName, arbName) { serverId, toolName ->
                val json = buildToolCallJson(serverId, toolName)
                val result = ChatResponseParser.parse(json, 10)
                assertTrue(result.reply.isNotBlank(), "reply must not be blank for tool call JSON")
                assertFalse(
                    result.reply.contains("mcpToolCall"),
                    "reply must not contain 'mcpToolCall', got: ${result.reply}"
                )
            }
        }
    }

    /**
     * Property 2a — Preservation: valid ChatResponse JSON → exact reply.
     *
     * For any random non-empty reply text, parsing a well-formed
     * ChatResponse JSON must return that exact reply unchanged.
     *
     * **Validates: Requirements 3.3**
     */
    @Test
    fun `Property 2a - valid ChatResponse JSON preserves exact reply text`() {
        runBlocking {
            checkAll(cfg, Arb.string(1..200, Codepoint.az())) { reply ->
                val json = buildChatResponseJson(reply)
                val result = ChatResponseParser.parse(json, 10)
                assertEquals(reply, result.reply, "reply must match input for valid JSON")
            }
        }
    }

    /**
     * Property 2b — Preservation: plain text → raw text returned.
     *
     * For any random non-JSON string, parse() must return the
     * raw text as reply (plain text fallback).
     *
     * **Validates: Requirements 3.4**
     */
    @Test
    fun `Property 2b - plain text input preserves raw text as reply`() {
        runBlocking {
            checkAll(cfg, Arb.string(1..200, Codepoint.az())) { text ->
                val result = ChatResponseParser.parse(text, 10)
                assertEquals(text, result.reply, "reply must equal raw text for non-JSON input")
            }
        }
    }

    private fun buildToolCallJson(serverId: String, toolName: String): String =
        """{"mcpToolCall":{"serverId":"$serverId","toolName":"$toolName","arguments":{}}}"""

    private fun buildChatResponseJson(reply: String): String =
        """{"reply":"$reply","actions":[],"references":[]}"""
}
