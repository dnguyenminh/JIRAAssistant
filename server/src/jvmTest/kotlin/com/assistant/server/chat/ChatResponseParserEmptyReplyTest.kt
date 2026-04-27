package com.assistant.server.chat

import com.assistant.server.chat.ChatResponseParser.EMPTY_REPLY_FALLBACK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for ChatResponseParser empty-reply fallback behavior.
 * Validates: Requirements 2.2 (fallback for empty/missing reply).
 */
internal class ChatResponseParserEmptyReplyTest {

    @Test
    fun `tool call JSON without reply field returns raw JSON as plain text fallback`() {
        val toolCallJson = """{"mcpToolCall":{"serverId":"test-srv","toolName":"get_ticket","arguments":{}}}"""
        val result = ChatResponseParser.parse(toolCallJson, 10)
        assertFalse(result.reply.isBlank(), "reply should not be blank")
        assertEquals(10, result.contextUsage)
    }

    @Test
    fun `JSON with empty reply string triggers empty-reply fallback`() {
        val json = """{"reply":"","actions":[],"references":[]}"""
        val result = ChatResponseParser.parse(json, 25)
        assertEquals(EMPTY_REPLY_FALLBACK, result.reply)
        assertEquals(25, result.contextUsage)
    }

    @Test
    fun `blank input returns startup message`() {
        val emptyResult = ChatResponseParser.parse("", 10)
        assertTrue(emptyResult.reply.contains("AI đang khởi động"))
        assertEquals(10, emptyResult.contextUsage)

        val whitespaceResult = ChatResponseParser.parse("   ", 10)
        assertTrue(whitespaceResult.reply.contains("AI đang khởi động"))
    }

    @Test
    fun `valid JSON with reply field returns correct reply`() {
        val json = """{"reply":"This is a valid reply","actions":[],"references":[]}"""
        val result = ChatResponseParser.parse(json, 15)
        assertEquals("This is a valid reply", result.reply)
        assertEquals(15, result.contextUsage)
    }
}
