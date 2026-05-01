package com.assistant.server.agent.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/**
 * Parses MCP tool inputSchema (JSON Schema) to extract
 * parameter names from the "properties" object.
 *
 * Returns empty list if schema is malformed or missing "properties".
 */
object InputSchemaParser {

    private val logger = LoggerFactory.getLogger(InputSchemaParser::class.java)

    fun extractParameterNames(inputSchema: JsonElement): List<String> {
        return try {
            val obj = inputSchema as? JsonObject ?: return emptyList()
            val properties = obj["properties"]?.jsonObject ?: return emptyList()
            properties.keys.toList()
        } catch (e: Exception) {
            logger.debug("Failed to parse inputSchema properties: {}", e.message)
            emptyList()
        }
    }
}
