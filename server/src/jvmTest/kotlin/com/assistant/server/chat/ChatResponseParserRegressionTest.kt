package com.assistant.server.chat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Regression tests for ChatResponseParser backward compatibility.
 * Requirements: 19.100, 19.101, 19.102
 */
class ChatResponseParserRegressionTest {

    @Test
    fun `two-param parse overload still works (Req 19_102)`() {
        val json = """{"reply":"Hello","actions":[],"references":[]}"""
        val result = ChatResponseParser.parse(json, 42)
        assertEquals("Hello", result.reply)
        assertEquals(42, result.contextUsage)
    }

    @Test
    fun `standard reply key decoded directly (Req 19_101)`() {
        val json = """{"reply":"Direct decode","actions":[],"references":[]}"""
        val result = ChatResponseParser.parse(json, 0, null, null)
        assertEquals("Direct decode", result.reply)
    }

    @Test
    fun `plain text returned as-is (Req 19_100)`() {
        val result = ChatResponseParser.parse("Just plain text", 0, null, null)
        assertEquals("Just plain text", result.reply)
    }

    @Test
    fun `alternative key text still works (Req 19_102)`() {
        val json = """{"text":"Alt text response","actions":[]}"""
        val result = ChatResponseParser.parse(json, 0)
        assertEquals("Alt text response", result.reply)
    }

    @Test
    fun `alternative key content still works (Req 19_102)`() {
        val json = """{"content":"Content response"}"""
        val result = ChatResponseParser.parse(json, 0)
        assertEquals("Content response", result.reply)
    }

    @Test
    fun `alternative key message still works (Req 19_102)`() {
        val json = """{"message":"Message response"}"""
        val result = ChatResponseParser.parse(json, 0)
        assertEquals("Message response", result.reply)
    }

    @Test
    fun `nested response_text still works (Req 19_102)`() {
        val json = """{"response":{"text":"Nested text"}}"""
        val result = ChatResponseParser.parse(json, 0)
        assertEquals("Nested text", result.reply)
    }

    @Test
    fun `response as primitive string still works (Req 19_102)`() {
        val json = """{"response":"Primitive response"}"""
        val result = ChatResponseParser.parse(json, 0)
        assertEquals("Primitive response", result.reply)
    }

    @Test
    fun `blank input returns startup message (Req 19_102)`() {
        val result = ChatResponseParser.parse("", 0)
        assertTrue(result.reply.contains("AI đang khởi động"))
    }

    @Test
    fun `code fences stripped before parsing (Req 19_102)`() {
        val wrapped = "```json\n{\"reply\":\"Fenced\",\"actions\":[],\"references\":[]}\n```"
        val result = ChatResponseParser.parse(wrapped, 0)
        assertEquals("Fenced", result.reply)
    }

    @Test
    fun `McpAgenticLoop parseMcpToolCall legacy still works (Req 19_102)`() {
        val response = """{"mcpToolCall":{"serverId":"local-knowledge-base","toolName":"get_ticket_info","arguments":{"ticketId":"ICL2-339"}}}"""
        val result = McpAgenticLoop.parseMcpToolCall(response)
        assertNotNull(result)
        assertEquals("get_ticket_info", result!!.toolName)
        assertEquals("local-knowledge-base", result.serverId)
    }

    @Test
    fun `McpToolCallFallback parseJsonToolName still works (Req 19_102)`() {
        val response = """{"tool_name":"get_ticket_info","tool_input":{"ticketId":"ICL2-339"}}"""
        val result = McpToolCallFallback.parseJsonToolName(response)
        assertNotNull(result)
        assertEquals("get_ticket_info", result!!.toolName)
    }

    @Test
    fun `McpToolCallFallback parseTextPattern still works (Req 19_102)`() {
        val response = """Tool Call: search_knowledge(query="test")"""
        val result = McpToolCallFallback.parseTextPattern(response)
        assertNotNull(result)
        assertEquals("search_knowledge", result!!.toolName)
    }

    @Test
    fun `four-param parse with null context works same as two-param (Req 19_102)`() {
        val json = """{"reply":"Same result","actions":[],"references":[]}"""
        val twoParam = ChatResponseParser.parse(json, 50)
        val fourParam = ChatResponseParser.parse(json, 50, null, null)
        assertEquals(twoParam.reply, fourParam.reply)
        assertEquals(twoParam.contextUsage, fourParam.contextUsage)
    }
}
