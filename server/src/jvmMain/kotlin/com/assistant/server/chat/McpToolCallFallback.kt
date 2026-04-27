package com.assistant.server.chat

import com.assistant.mcp.models.McpToolCallRequest
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Fallback parsers for AI-hallucinated tool call formats.
 * Handles: {"tool_name":"...","tool_input":{...}} and "Tool Call: name(args)".
 * Requirements: 19.67
 */
internal object McpToolCallFallback {

    private val logger = LoggerFactory.getLogger(McpToolCallFallback::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val TEXT_PATTERN = Regex(
        """Tool\s*Call:\s*(\w+)\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE
    )

    /** Parse {"tool_name":"...","tool_input":{...}} JSON from AI. */
    fun parseJsonToolName(response: String): McpToolCallRequest? {
        val idx = response.indexOf("\"tool_name\"")
        if (idx < 0) return null
        return try {
            val braceStart = response.lastIndexOf('{', idx)
            if (braceStart < 0) return null
            val outer = extractJson(response, braceStart) ?: return null
            val parsed = json.parseToJsonElement(outer).jsonObject
            val rawName = parsed["tool_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val inputObj = parsed["tool_input"]?.jsonObject
            val args = buildArgsFromJson(inputObj)
            val toolName = mapToolName(rawName) ?: inferToolFromArgs(args) ?: return null
            logger.info("[Fallback] JSON: tool=$toolName args=$args")
            buildRequest(toolName, args)
        } catch (e: Exception) {
            logger.debug("[Fallback] JSON parse failed: ${e.message}"); null
        }
    }

    /** Parse "Tool Call: tool_name(param=value)" text pattern. */
    fun parseTextPattern(response: String): McpToolCallRequest? {
        val match = TEXT_PATTERN.find(response) ?: return null
        val rawName = match.groupValues[1]
        val toolName = mapToolName(rawName) ?: return null
        val args = parseTextArgs(match.groupValues[2])
        logger.info("[Fallback] Text: tool=$toolName args=$args")
        return buildRequest(toolName, args)
    }

    private fun buildRequest(toolName: String, args: Map<String, String>) =
        McpToolCallRequest(
            serverId = LocalKBToolExecutor.SERVER_ID,
            toolName = toolName,
            arguments = JsonObject(args.mapValues { (_, v) -> JsonPrimitive(v) })
        )

    private fun buildArgsFromJson(obj: JsonObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        for ((k, v) in obj) result[mapArgName(k)] = v.jsonPrimitive.contentOrNull ?: v.toString()
        return result
    }

    private fun parseTextArgs(argsStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (part in argsStr.split(",")) {
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim()
                val value = kv[1].trim().removeSurrounding("\"").removeSurrounding("'")
                result[mapArgName(key)] = value
            }
        }
        return result
    }

    fun mapToolName(raw: String): String? = when {
        raw == "get_ticket_info" || raw == "search_knowledge" || raw == "search_relationships" -> raw
        raw == "local_knowledge_base" || raw == "local-knowledge-base" -> null // server name, not tool — need tool_input to decide
        raw.contains("ticket", ignoreCase = true) -> "get_ticket_info"
        raw.contains("relationship", ignoreCase = true) || raw.contains("rel", ignoreCase = true) -> "search_relationships"
        raw.contains("search", ignoreCase = true) || raw.contains("knowledge", ignoreCase = true) -> "search_knowledge"
        else -> null
    }

    fun mapArgName(raw: String): String = when {
        raw.contains("ticket", ignoreCase = true) || raw.contains("id", ignoreCase = true) -> "ticketId"
        raw.contains("query", ignoreCase = true) || raw.contains("search", ignoreCase = true) -> "query"
        raw.contains("node", ignoreCase = true) -> "ticketId"
        else -> raw
    }

    /** Infer tool name from arguments when AI sends server name as tool_name. */
    private fun inferToolFromArgs(args: Map<String, String>): String? = when {
        args.containsKey("ticketId") -> "get_ticket_info"
        args.containsKey("query") && args.containsKey("chunkType") -> "search_knowledge"
        args.any { it.key.contains("relation", ignoreCase = true) } -> "search_relationships"
        args.containsKey("query") -> "search_knowledge"
        else -> null
    }

    private fun extractJson(text: String, start: Int): String? {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }
}
