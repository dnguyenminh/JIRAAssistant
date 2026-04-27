package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama

import com.assistant.mcp.models.McpAggregatedTool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("McpToolConverter")

/**
 * Converts [McpAggregatedTool] directly to [OllamaTool],
 * preserving the full JSON Schema from inputSchema.
 *
 * This bypasses [com.assistant.agent.models.ToolDescriptor]
 * which loses parameter type/required info.
 */
fun McpAggregatedTool.toOllamaTool(): OllamaTool {
    val toolName = "mcp_${serverName}_${name}"
    val params = parseInputSchema(inputSchema as? JsonObject)
    return OllamaTool(
        function = OllamaToolFunction(
            name = toolName,
            description = description,
            parameters = params
        )
    )
}

/**
 * Parses a JSON Schema object into [OllamaToolParameters].
 * Extracts properties, types, descriptions, and required list.
 */
internal fun parseInputSchema(
    schema: JsonObject?
): OllamaToolParameters {
    if (schema == null) {
        return OllamaToolParameters(
            properties = emptyMap<String, OllamaToolProperty>()
        )
    }

    val propsObj = schema["properties"]?.jsonObject
    val requiredArr = schema["required"]

    val properties = propsObj?.mapValues { (_, value) ->
        val propObj = value.jsonObject
        val type = extractString(propObj, "type") ?: "string"
        val desc = extractString(propObj, "description") ?: ""
        OllamaToolProperty(type = type, description = desc)
    } ?: emptyMap()

    val required = parseRequiredList(requiredArr)

    return OllamaToolParameters(
        properties = properties,
        required = required
    )
}

private fun extractString(obj: JsonObject, key: String): String? {
    return (obj[key] as? JsonPrimitive)?.content
}

private fun parseRequiredList(element: Any?): List<String> {
    if (element == null) return emptyList()
    return try {
        (element as? JsonArray)?.map { it.jsonPrimitive.content }
            ?: emptyList()
    } catch (e: Exception) {
        log.warn("Failed to parse required list: {}", e.message)
        emptyList()
    }
}
