package com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ollama chat completion request.
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = true,
    val tools: List<OllamaTool>? = null,
    val options: OllamaOptions? = null
)

/**
 * A single message in an Ollama chat conversation.
 */
@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String = "",
    @SerialName("tool_calls")
    val toolCalls: List<OllamaToolCall>? = null
)

/**
 * Ollama streaming/non-streaming chat response chunk.
 */
@Serializable
data class OllamaChatResponse(
    val model: String = "",
    val message: OllamaChatMessage = OllamaChatMessage(role = "assistant"),
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
    @SerialName("total_duration") val totalDuration: Long? = null,
    @SerialName("eval_count") val evalCount: Int? = null
)

/**
 * Ollama model inference options.
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_predict") val numPredict: Int? = null
)
