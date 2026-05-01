package com.assistant.ai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Data models for Ollama /api/chat endpoint with native tool calling.
 * Supports structured tool_calls in responses instead of text-based parsing.
 */

/** Request body for POST /api/chat. */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val tools: List<OllamaChatToolDef> = emptyList(),
    val stream: Boolean = false
)

/** A single message in the chat conversation. */
@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String = "",
    @SerialName("tool_calls") val toolCalls: List<OllamaChatToolCall>? = null
)

/** A structured tool call returned by the model. */
@Serializable
data class OllamaChatToolCall(
    val function: OllamaChatFunctionCall
)

/** Function name + arguments inside a tool call. */
@Serializable
data class OllamaChatFunctionCall(
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap())
)

/** Response from POST /api/chat. */
@Serializable
data class OllamaChatResponse(
    val model: String = "",
    val message: OllamaChatMessage = OllamaChatMessage(role = "assistant"),
    @SerialName("done") val done: Boolean = true,
    @SerialName("done_reason") val doneReason: String? = null
)

/** Tool definition in Ollama's format. */
@Serializable
data class OllamaChatToolDef(
    val type: String = "function",
    val function: OllamaChatFunctionDef
)

/** Function definition inside a tool def. */
@Serializable
data class OllamaChatFunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonElement = JsonObject(emptyMap())
)
