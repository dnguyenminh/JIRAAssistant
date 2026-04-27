package com.assistant.server.mcp.internal

import kotlinx.serialization.json.*

/**
 * Utility functions for building JSON Schema objects for tool inputSchema.
 * Requirements: AC 6.108
 */

/** Build a JSON Schema object with type:"object", properties, and required. */
internal fun buildSchema(
    properties: JsonObject = JsonObject(emptyMap()),
    required: List<String> = emptyList()
): JsonElement = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("properties", properties)
    put("required", JsonArray(required.map { JsonPrimitive(it) }))
}

/** Create a string property with description. */
internal fun stringProp(description: String) = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
}

/** Create a string property with enum constraint. */
internal fun enumProp(
    description: String,
    values: List<String>
) = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
    put("enum", JsonArray(values.map { JsonPrimitive(it) }))
}

/** Create an integer property with description and optional default. */
internal fun intProp(
    description: String,
    default: Int? = null
) = buildJsonObject {
    put("type", JsonPrimitive("integer"))
    put("description", JsonPrimitive(description))
    if (default != null) put("default", JsonPrimitive(default))
}

/** Create a boolean property with description and optional default. */
internal fun boolProp(
    description: String,
    default: Boolean? = null
) = buildJsonObject {
    put("type", JsonPrimitive("boolean"))
    put("description", JsonPrimitive(description))
    if (default != null) put("default", JsonPrimitive(default))
}

/** Create an object property with description. */
internal fun objectProp(description: String) = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("description", JsonPrimitive(description))
}
