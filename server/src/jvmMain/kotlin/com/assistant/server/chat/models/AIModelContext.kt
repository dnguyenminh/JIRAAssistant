package com.assistant.server.chat.models

/**
 * Provider type + model name context for cache-keyed format detection.
 * Requirements: 19.96, 19.97
 */
data class AIModelContext(
    val providerType: String,  // "OLLAMA", "GEMINI", "LM_STUDIO"
    val modelName: String      // "gemma3:4b", "gemini-2.0-flash"
) {
    /** Cache key suffix: "OLLAMA:gemma3:4b" */
    val cacheKeySuffix: String get() = "$providerType:$modelName"
}
