package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.ParsedToolCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses tool call JSON from CLI stdout and formats tool result JSON for stdin.
 *
 * Wire format:
 * - Request:  {"toolCall":{"name":"...","arguments":{...}}}
 * - Response: {"toolResult":{"name":"...","success":true/false,"data":"...","error":"..."}}
 */
object ToolCallProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val TOOL_CALL_MARKER = "\"toolCall\""
    private const val JSON_START = "{\"toolCall\""

    /**
     * Extracts a [ParsedToolCall] from a stdout line containing tool call JSON.
     * Returns null on ANY failure — never throws exceptions.
     */
    fun parseToolCall(line: String): ParsedToolCall? = try {
        if (!line.contains(TOOL_CALL_MARKER)) return null
        val jsonStart = line.indexOf(JSON_START)
        if (jsonStart < 0) return null
        val jsonStr = line.substring(jsonStart)
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val toolCall = root["toolCall"]?.jsonObject ?: return null
        val name = toolCall["name"]?.jsonPrimitive?.content ?: return null
        val args = extractArguments(toolCall)
        ParsedToolCall(name, args)
    } catch (_: Exception) {
        null
    }

    /**
     * Formats a tool result as protocol JSON with proper escaping.
     */
    fun formatToolResult(
        name: String,
        success: Boolean,
        data: String,
        error: String
    ): String {
        val result = ToolResultEnvelope(
            toolResult = ToolResultPayload(name, success, data, error)
        )
        return json.encodeToString(ToolResultEnvelope.serializer(), result)
    }

    private fun extractArguments(toolCall: JsonObject): Map<String, String> {
        val argsElement = toolCall["arguments"] ?: return emptyMap()
        val argsObj = argsElement.jsonObject
        return argsObj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
    }
}

/** Internal DTO for serializing tool result responses. */
@Serializable
internal data class ToolResultEnvelope(val toolResult: ToolResultPayload)

@Serializable
internal data class ToolResultPayload(
    val name: String,
    val success: Boolean,
    val data: String,
    val error: String
)
