package com.assistant.server.chat

import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Detects reply field in AI JSON responses via heuristic + cache.
 * Priority: cached path → known keys → longest string field.
 * Requirements: 19.87, 19.88, 19.89, 19.90, 19.91, 19.99
 */
internal object ResponseFormatDetector {

    private const val CACHE_PREFIX = "ai_response_format:"
    private val logger = LoggerFactory.getLogger(ResponseFormatDetector::class.java)

    data class DetectedReply(val path: String, val text: String)

    private val KNOWN_KEYS = listOf("reply", "text", "content", "message", "answer", "output")
    private val NESTED_KEYS = listOf("text", "content", "reply", "message")

    /** Detect reply field: cached path → known keys → longest string. */
    suspend fun detect(
        jsonObj: JsonObject,
        modelCtx: AIModelContext?,
        settingsRepo: SettingsRepository?
    ): DetectedReply? {
        val cached = tryCache(jsonObj, modelCtx, settingsRepo)
        if (cached != null) return cached
        val known = tryKnownKeys(jsonObj)
        if (known != null) {
            saveCache(modelCtx, settingsRepo, known.path)
            return known
        }
        val longest = findLongestString(jsonObj)
        if (longest != null) {
            saveCache(modelCtx, settingsRepo, longest.path)
            return longest
        }
        return null
    }

    /** Try cached JSON path. Invalidate if stale (Req: 19.90). */
    private suspend fun tryCache(
        obj: JsonObject, ctx: AIModelContext?, repo: SettingsRepository?
    ): DetectedReply? {
        if (ctx == null || repo == null) return null
        val key = "$CACHE_PREFIX${ctx.cacheKeySuffix}"
        val path = repo.get(key)
        if (path.isNullOrBlank()) return null
        val text = extractByPath(obj, path)
        if (text != null) {
            logger.debug("[ResponseFormatDetector] Cache hit: $path")
            return DetectedReply(path, text)
        }
        logger.info("[ResponseFormatDetector] Stale cache for $key, invalidating")
        repo.put(key, "")
        return null
    }

    /** Try known keys: reply, text, content, message, answer, output, response.* */
    private fun tryKnownKeys(obj: JsonObject): DetectedReply? {
        for (k in KNOWN_KEYS) {
            val text = obj[k]?.primitiveContentOrNull()
            if (!text.isNullOrBlank()) return DetectedReply(k, text)
        }
        val resp = obj["response"]
        if (resp is JsonPrimitive) {
            val t = resp.contentOrNull
            if (!t.isNullOrBlank()) return DetectedReply("response", t)
        }
        if (resp is JsonObject) {
            for (k in NESTED_KEYS) {
                val t = resp[k]?.primitiveContentOrNull()
                if (!t.isNullOrBlank()) return DetectedReply("response.$k", t)
            }
        }
        return null
    }

    /** Find longest string field in top-level + 1-level nested. */
    private fun findLongestString(obj: JsonObject): DetectedReply? {
        var best: DetectedReply? = null
        for ((k, v) in obj) {
            val text = v.primitiveContentOrNull()
            if (text != null && text.length > (best?.text?.length ?: 0)) {
                best = DetectedReply(k, text)
            }
            if (v is JsonObject) {
                for ((nk, nv) in v) {
                    val nt = nv.primitiveContentOrNull()
                    if (nt != null && nt.length > (best?.text?.length ?: 0)) {
                        best = DetectedReply("$k.$nk", nt)
                    }
                }
            }
        }
        return best
    }

    /** Extract string value by dot-separated path. */
    fun extractByPath(obj: JsonObject, path: String): String? {
        val parts = path.split(".")
        var current: JsonElement = obj
        for (part in parts) {
            current = (current as? JsonObject)?.get(part) ?: return null
        }
        return (current as? JsonPrimitive)?.contentOrNull
    }

    private suspend fun saveCache(
        ctx: AIModelContext?, repo: SettingsRepository?, path: String
    ) {
        if (ctx == null || repo == null) return
        val key = "$CACHE_PREFIX${ctx.cacheKeySuffix}"
        repo.put(key, path)
        logger.debug("[ResponseFormatDetector] Cached: $key → $path")
    }

    private fun JsonElement.primitiveContentOrNull(): String? {
        val p = this as? JsonPrimitive ?: return null
        if (!p.isString) return null
        return p.contentOrNull
    }
}
