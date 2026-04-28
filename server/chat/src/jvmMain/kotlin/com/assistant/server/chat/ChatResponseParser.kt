package com.assistant.server.chat

import com.assistant.chat.ChatResponse
import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*

/**
 * Utility for parsing AI response text into ChatResponse.
 * Supports cache-first format detection via ResponseFormatDetector.
 * Requirements: 19.5, 19.8, 19.87–19.91, 19.100, 19.101, 19.102
 */
internal object ChatResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** Fallback message when parsed reply is blank (Req: 2.2). */
    internal const val EMPTY_REPLY_FALLBACK =
        "Tôi đã thu thập thông tin nhưng không thể tổng hợp câu trả lời. Vui lòng thử lại hoặc đặt câu hỏi cụ thể hơn."

    /** Backward-compatible parse without model context (Req: 19.102). */
    fun parse(raw: String, usage: Int): ChatResponse =
        parse(raw, usage, null, null)

    /** Cache-first parse with model context for format detection. */
    fun parse(
        raw: String, usage: Int,
        modelCtx: AIModelContext?,
        settingsRepo: SettingsRepository?
    ): ChatResponse {
        if (raw.isBlank()) return ChatResponse(
            reply = "AI đang khởi động, vui lòng thử lại sau vài giây.",
            contextUsage = usage
        )
        // Step 1: try standard decode (Req: 19.101)
        tryStandardDecode(raw, usage)?.let { return ensureNonEmptyReply(it) }
        // Step 2: extract JSON, try detector + legacy fallbacks
        return ensureNonEmptyReply(tryExtractJson(raw, usage, modelCtx, settingsRepo))
    }

    /** Replace blank reply with fallback message (Req: 2.2). */
    private fun ensureNonEmptyReply(response: ChatResponse): ChatResponse =
        if (response.reply.isBlank()) response.copy(reply = EMPTY_REPLY_FALLBACK)
        else response

    /** Try standard ChatResponse decode (code fences stripped). */
    private fun tryStandardDecode(raw: String, usage: Int): ChatResponse? = try {
        val cleaned = stripCodeFences(raw)
        json.decodeFromString<ChatResponse>(cleaned).copy(contextUsage = usage)
    } catch (_: Exception) { null }

    private fun tryExtractJson(
        raw: String, usage: Int,
        modelCtx: AIModelContext?, settingsRepo: SettingsRepository?
    ): ChatResponse {
        val extracted = extractJsonObject(raw)
        if (extracted != null) {
            // Try standard ChatResponse on extracted JSON
            try {
                return json.decodeFromString<ChatResponse>(extracted)
                    .copy(contextUsage = usage)
            } catch (_: Exception) { /* try detector + alternatives */ }
            // Try ResponseFormatDetector (cache → known → longest)
            val detected = tryDetectorParse(extracted, usage, modelCtx, settingsRepo)
            if (detected != null) return detected
            // Legacy alternative formats (Req: 19.102)
            val alt = tryAlternativeJsonFormats(extracted, usage)
            if (alt != null) return alt
        }
        // Plain text fallback (Req: 19.100)
        return ChatResponse(reply = raw, contextUsage = usage)
    }

    /** Use ResponseFormatDetector for cache-first detection. */
    private fun tryDetectorParse(
        jsonStr: String, usage: Int,
        modelCtx: AIModelContext?, settingsRepo: SettingsRepository?
    ): ChatResponse? {
        val obj = try {
            json.parseToJsonElement(jsonStr).jsonObject
        } catch (_: Exception) { return null }
        val detected = runBlocking {
            ResponseFormatDetector.detect(obj, modelCtx, settingsRepo)
        } ?: return null
        val actions = extractActions(obj)
        val refs = extractReferences(obj)
        return ChatResponse(
            reply = detected.text, actions = actions,
            references = refs, contextUsage = usage
        )
    }

    private fun extractActions(obj: JsonObject) = try {
        obj["actions"]?.jsonArray?.map {
            json.decodeFromJsonElement<com.assistant.chat.ChatAction>(it)
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun extractReferences(obj: JsonObject) = try {
        obj["references"]?.jsonArray?.map {
            json.decodeFromJsonElement<com.assistant.chat.ChatReference>(it)
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** Handle AI JSON with non-standard keys like "response", "text", "content". */
    private fun tryAlternativeJsonFormats(jsonStr: String, usage: Int): ChatResponse? = try {
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        val reply = extractReplyFromJson(obj)
        if (reply != null) {
            val actions = obj["actions"]?.jsonArray?.map {
                json.decodeFromJsonElement<com.assistant.chat.ChatAction>(it)
            } ?: emptyList()
            val refs = obj["references"]?.jsonArray?.map {
                json.decodeFromJsonElement<com.assistant.chat.ChatReference>(it)
            } ?: emptyList()
            ChatResponse(reply = reply, actions = actions, references = refs, contextUsage = usage)
        } else null
    } catch (_: Exception) { null }

    /** Extract reply text from various JSON structures AI might produce. */
    private fun extractReplyFromJson(obj: JsonObject): String? {
        // Direct keys: "reply", "text", "content", "message"
        for (key in listOf("reply", "text", "content", "message")) {
            obj[key]?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        // Nested: "response.text", "response.content"
        obj["response"]?.let { resp ->
            if (resp is JsonPrimitive) return resp.contentOrNull
            if (resp is JsonObject) {
                for (key in listOf("text", "content", "reply", "message")) {
                    resp[key]?.jsonPrimitive?.contentOrNull?.let { return it }
                }
            }
        }
        return null
    }

    /** Extract first balanced JSON object {...} from text. */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    /** Strip markdown code fences from AI response. */
    private fun stripCodeFences(text: String): String {
        val t = text.trim()
        val start = t.indexOf("```")
        if (start < 0) return t
        val after = t.substring(start + 3)
        val nl = after.indexOf('\n')
        if (nl < 0) return after.trim()
        val content = after.substring(nl + 1)
        val end = content.lastIndexOf("```")
        return if (end >= 0) content.substring(0, end).trim() else content.trim()
    }
}
