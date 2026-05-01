package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Ollama tool definition using JSON Schema format.
 */
@Serializable
data class OllamaTool(
    val type: String = "function",
    val function: OllamaToolFunction
)

/**
 * Function metadata within an Ollama tool definition.
 */
@Serializable
data class OllamaToolFunction(
    val name: String,
    val description: String,
    val parameters: OllamaToolParameters
)

/**
 * JSON Schema parameters for an Ollama tool function.
 */
@Serializable
data class OllamaToolParameters(
    val type: String = "object",
    val properties: Map<String, OllamaToolProperty>,
    val required: List<String> = emptyList()
)

/**
 * A single property in an Ollama tool's parameter schema.
 */
@Serializable
data class OllamaToolProperty(
    val type: String,
    val description: String
)

/**
 * A tool call returned by Ollama in a chat response message.
 */
@Serializable
data class OllamaToolCall(
    val function: OllamaToolCallFunction
)

/**
 * Function invocation details within an Ollama tool call.
 * Arguments use JsonElement to handle mixed types (strings, arrays, numbers).
 */
@Serializable
data class OllamaToolCallFunction(
    val name: String,
    val arguments: Map<String, JsonElement> = emptyMap()
) {
    /** Convert arguments to Map<String, String> for ToolExecutionBridge. */
    fun argumentsAsStrings(): Map<String, String> =
        arguments.mapValues { (_, v) -> v.toString().trim('"') }
}
