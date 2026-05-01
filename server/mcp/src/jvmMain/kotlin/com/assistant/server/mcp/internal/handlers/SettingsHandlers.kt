package com.assistant.server.mcp.internal.handlers

import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.UserContext
import com.assistant.settings.SettingsRepository
import kotlinx.serialization.json.*

/**
 * Settings tool handlers — get_settings, update_setting, get_setting.
 * Requirements: AC 6.89–6.91
 */
class SettingsHandlers(private val settingsRepository: SettingsRepository) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleGetSettings(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val all = settingsRepository.getAll()
        val result = buildJsonObject {
            all.forEach { (k, v) -> put(k, maskSensitive(k, v)) }
        }
        return textResponse(result.toString())
    }

    suspend fun handleUpdateSetting(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val key = args.str("key") ?: return missingField("key")
        val value = args.str("value") ?: return missingField("value")
        settingsRepository.put(key, value)
        val result = buildJsonObject {
            put("success", true)
            put("key", key)
        }
        return textResponse(result.toString())
    }

    suspend fun handleGetSetting(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val key = args.str("key") ?: return missingField("key")
        val value = settingsRepository.get(key)
        val result = buildJsonObject {
            put("key", key)
            put("value", value?.let { maskSensitive(key, it) } ?: "null")
            put("found", value != null)
        }
        return textResponse(result.toString())
    }

    private fun maskSensitive(key: String, value: String): String {
        val sensitiveKeys = setOf("jwtSecret", "encryptionKey", "apiKey")
        if (sensitiveKeys.any { key.contains(it, ignoreCase = true) }) {
            return if (value.length > 4) "${"*".repeat(value.length - 4)}${value.takeLast(4)}" else "****"
        }
        return value
    }
}
