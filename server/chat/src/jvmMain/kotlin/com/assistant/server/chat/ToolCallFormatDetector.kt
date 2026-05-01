package com.assistant.server.chat

import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Detects tool call format in AI responses via cache + known patterns.
 * Priority: cached format → mcpToolCall → tool_name_input → text pattern.
 * Requirements: 19.92, 19.93, 19.94, 19.95, 19.99, 19.102
 */
internal object ToolCallFormatDetector {

    private const val CACHE_PREFIX = "ai_tool_call_format:"
    private val logger = LoggerFactory.getLogger(ToolCallFormatDetector::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    object Formats {
        const val MCP_TOOL_CALL = "mcpToolCall"
        const val TOOL_NAME_INPUT = "tool_name_input"
        const val TEXT_PATTERN = "text_pattern"
    }

    /** Try cached format first, then all known patterns. */
    suspend fun detect(
        response: String,
        modelCtx: AIModelContext?,
        settingsRepo: SettingsRepository?
    ): McpToolCallRequest? {
        val cached = tryCachedFormat(response, modelCtx, settingsRepo)
        if (cached != null) return cached
        return tryAllPatterns(response, modelCtx, settingsRepo)
    }

    private suspend fun tryCachedFormat(
        response: String, ctx: AIModelContext?, repo: SettingsRepository?
    ): McpToolCallRequest? {
        if (ctx == null || repo == null) return null
        val key = "$CACHE_PREFIX${ctx.cacheKeySuffix}"
        val format = repo.get(key)
        if (format.isNullOrBlank()) return null
        val result = parseByFormat(response, format)
        if (result != null) {
            logger.debug("[ToolCallFormatDetector] Cache hit: $format")
            return result
        }
        logger.info("[ToolCallFormatDetector] Stale cache for $key, invalidating")
        repo.put(key, "")
        return null
    }

    private suspend fun tryAllPatterns(
        response: String, ctx: AIModelContext?, repo: SettingsRepository?
    ): McpToolCallRequest? {
        parseMcpToolCallJson(response)?.let {
            saveCache(ctx, repo, Formats.MCP_TOOL_CALL); return it
        }
        McpToolCallFallback.parseJsonToolName(response)?.let {
            saveCache(ctx, repo, Formats.TOOL_NAME_INPUT); return it
        }
        McpToolCallFallback.parseTextPattern(response)?.let {
            saveCache(ctx, repo, Formats.TEXT_PATTERN); return it
        }
        return null
    }

    private fun parseByFormat(response: String, format: String): McpToolCallRequest? =
        when (format) {
            Formats.MCP_TOOL_CALL -> parseMcpToolCallJson(response)
            Formats.TOOL_NAME_INPUT -> McpToolCallFallback.parseJsonToolName(response)
            Formats.TEXT_PATTERN -> McpToolCallFallback.parseTextPattern(response)
            else -> null
        }

    /** Parse {"mcpToolCall": {...}} — same logic as McpAgenticLoop primary. */
    private fun parseMcpToolCallJson(response: String): McpToolCallRequest? {
        val idx = response.indexOf("\"mcpToolCall\"")
        if (idx < 0) return null
        return try {
            val braceStart = response.lastIndexOf('{', idx)
            if (braceStart < 0) return null
            val outer = extractJsonObject(response, braceStart) ?: return null
            val parsed = json.parseToJsonElement(outer).jsonObject
            val inner = parsed["mcpToolCall"]?.jsonObject ?: return null
            json.decodeFromJsonElement<McpToolCallRequest>(inner)
        } catch (_: Exception) { null }
    }

    private fun extractJsonObject(text: String, start: Int): String? {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private suspend fun saveCache(
        ctx: AIModelContext?, repo: SettingsRepository?, format: String
    ) {
        if (ctx == null || repo == null) return
        repo.put("$CACHE_PREFIX${ctx.cacheKeySuffix}", format)
        logger.debug("[ToolCallFormatDetector] Cached: ${ctx.cacheKeySuffix} → $format")
    }
}
