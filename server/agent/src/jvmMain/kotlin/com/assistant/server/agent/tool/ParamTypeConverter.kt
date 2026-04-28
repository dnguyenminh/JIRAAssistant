package com.assistant.server.agent.tool

import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Converts string parameter values to typed JSON values
 * based on the MCP tool's inputSchema.
 *
 * Conversion rules:
 * - "integer"/"number" → JsonPrimitive(Long/Double)
 * - "boolean" → JsonPrimitive(Boolean)
 * - Conversion failure → keep as JsonPrimitive(string)
 */
object ParamTypeConverter {

    private val logger = LoggerFactory.getLogger(ParamTypeConverter::class.java)

    fun convert(
        params: Map<String, String>,
        inputSchema: JsonElement
    ): JsonObject {
        val schemaProps = extractSchemaProperties(inputSchema)
        return JsonObject(params.mapValues { (key, value) ->
            resolveType(key, value, schemaProps)
        })
    }

    internal fun resolveType(
        key: String, value: String, schemaProperties: JsonObject?
    ): JsonPrimitive {
        val type = schemaProperties
            ?.get(key)?.jsonObject
            ?.get("type")?.jsonPrimitive?.contentOrNull
            ?: return JsonPrimitive(value)
        return convertByType(key, value, type)
    }

    private fun convertByType(
        key: String, value: String, type: String
    ): JsonPrimitive = when (type) {
        "integer", "number" -> convertNumeric(key, value)
        "boolean" -> convertBoolean(key, value)
        else -> JsonPrimitive(value)
    }

    private fun convertNumeric(key: String, value: String): JsonPrimitive {
        value.toLongOrNull()?.let {
            logger.debug("Param '{}': '{}' → Long", key, value)
            return JsonPrimitive(it)
        }
        value.toDoubleOrNull()?.let {
            logger.debug("Param '{}': '{}' → Double", key, value)
            return JsonPrimitive(it)
        }
        return JsonPrimitive(value)
    }

    private fun convertBoolean(key: String, value: String): JsonPrimitive {
        return when (value.lowercase()) {
            "true" -> JsonPrimitive(true).also {
                logger.debug("Param '{}': '{}' → Boolean(true)", key, value)
            }
            "false" -> JsonPrimitive(false).also {
                logger.debug("Param '{}': '{}' → Boolean(false)", key, value)
            }
            else -> JsonPrimitive(value)
        }
    }

    private fun extractSchemaProperties(schema: JsonElement): JsonObject? {
        return try {
            (schema as? JsonObject)?.get("properties")?.jsonObject
        } catch (_: Exception) { null }
    }
}
