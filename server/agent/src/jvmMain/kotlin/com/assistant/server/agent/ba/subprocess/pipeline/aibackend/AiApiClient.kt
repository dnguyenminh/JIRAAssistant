package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

/**
 * API-specific extension of [AiBackend] with base URL and model properties.
 */
interface AiApiClient : AiBackend {
    val baseUrl: String
    val model: String
}
