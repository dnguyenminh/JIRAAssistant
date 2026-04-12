package com.assistant.frontend.models

import kotlinx.serialization.Serializable

/**
 * Response from /api/chat/model-info endpoint.
 */
@Serializable
data class ModelInfoResponse(
    val modelName: String = "",
    val provider: String = "",
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val maxTokens: Int = 0
)
