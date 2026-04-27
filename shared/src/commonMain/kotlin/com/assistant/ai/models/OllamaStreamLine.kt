package com.assistant.ai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single NDJSON line from Ollama streaming response.
 * Each line contains a partial text token in `response` and metadata.
 * The final line has `done = true` with optional `doneReason`.
 *
 * All fields have safe defaults for backward compatibility and
 * resilient deserialization of partial/incomplete JSON lines.
 *
 * Requirements: 1.1, 1.2
 */
@Serializable
data class OllamaStreamLine(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false,
    @SerialName("done_reason") val doneReason: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)
