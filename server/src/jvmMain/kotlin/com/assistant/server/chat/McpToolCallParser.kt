package com.assistant.server.chat

import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Tool call parsing logic extracted from McpAgenticLoop to keep it ≤ 200 lines.
 * Requirements: 6.53, 19.94, 19.102
 */
internal object McpToolCallParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Parse AI response for tool call — cache-first via detector when context available. Req: 6.53, 19.94 */
    fun parse(
        response: String,
        modelCtx: AIModelContext? = null,
        settingsRepo: SettingsRepository? = null
    ): McpToolCallRequest? {
        if (modelCtx != null && settingsRepo != null) {
            return runBlocking {
                ToolCallFormatDetector.detect(response, modelCtx, settingsRepo)
            }
        }
        return parseLegacy(response)
    }

    /** Legacy tool call parsing without cache. Req: 19.102 */
    private fun parseLegacy(response: String): McpToolCallRequest? {
        val idx = response.indexOf("\"mcpToolCall\"")
        if (idx >= 0) {
            val parsed = tryParseJsonFormat(response, idx)
            if (parsed != null) return parsed
        }
        val jsonFallback = McpToolCallFallback.parseJsonToolName(response)
        if (jsonFallback != null) return jsonFallback
        return McpToolCallFallback.parseTextPattern(response)
    }

    private fun tryParseJsonFormat(response: String, idx: Int): McpToolCallRequest? = try {
        val braceStart = response.lastIndexOf('{', idx)
        if (braceStart >= 0) {
            val outer = extractJsonObject(response, braceStart)
            if (outer != null) {
                val parsed = json.parseToJsonElement(outer).jsonObject
                val inner = parsed["mcpToolCall"]?.jsonObject
                if (inner != null) json.decodeFromJsonElement<McpToolCallRequest>(inner) else null
            } else null
        } else null
    } catch (_: Exception) { null }

    /** Extract balanced JSON object starting at braceStart. */
    fun extractJsonObject(text: String, start: Int): String? {
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
