package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.McpError
import kotlinx.serialization.json.*

/**
 * Validates tool call arguments against the tool's inputSchema.
 * Requirements: AC 6.112
 */
object ArgumentValidator {

    /** Validate arguments against tool definition's inputSchema. */
    fun validate(toolDef: InternalToolDefinition, arguments: JsonObject) {
        val schema = toolDef.inputSchema.jsonObject
        validateRequiredFields(schema, arguments, toolDef.name)
        val properties = schema["properties"]?.jsonObject ?: return
        arguments.forEach { (key, value) ->
            validateField(key, value, properties, toolDef.name)
        }
    }

    private fun validateRequiredFields(
        schema: JsonObject,
        arguments: JsonObject,
        toolName: String
    ) {
        val required = schema["required"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: return
        val missing = required.filter { it !in arguments }
        if (missing.isNotEmpty()) {
            throw McpError(
                McpError.INVALID_PARAMS,
                "Tool '$toolName': missing required fields: ${missing.joinToString()}"
            )
        }
    }

    private fun validateField(
        key: String,
        value: JsonElement,
        properties: JsonObject,
        toolName: String
    ) {
        val propSchema = properties[key]?.jsonObject ?: return
        val expectedType = propSchema["type"]?.jsonPrimitive?.contentOrNull
        validateType(key, value, expectedType, toolName)
        validateEnum(key, value, propSchema, toolName)
    }

    private fun validateType(
        key: String,
        value: JsonElement,
        expectedType: String?,
        toolName: String
    ) {
        if (expectedType == null) return
        val valid = when (expectedType) {
            "string" -> value is JsonPrimitive && value.isString
            "integer" -> value is JsonPrimitive && value.intOrNull != null
            "boolean" -> value is JsonPrimitive && value.booleanOrNull != null
            "object" -> value is JsonObject
            "array" -> value is JsonArray
            else -> true
        }
        if (!valid) {
            throw McpError(
                McpError.INVALID_PARAMS,
                "Tool '$toolName': field '$key' must be $expectedType"
            )
        }
    }

    private fun validateEnum(
        key: String,
        value: JsonElement,
        propSchema: JsonObject,
        toolName: String
    ) {
        val enumValues = propSchema["enum"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: return
        val actual = (value as? JsonPrimitive)?.contentOrNull ?: return
        if (actual !in enumValues) {
            throw McpError(
                McpError.INVALID_PARAMS,
                "Tool '$toolName': field '$key' must be one of: ${enumValues.joinToString()}"
            )
        }
    }
}
