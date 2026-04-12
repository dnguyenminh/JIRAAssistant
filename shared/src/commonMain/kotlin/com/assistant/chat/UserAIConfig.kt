package com.assistant.chat

import kotlinx.serialization.Serializable

/**
 * Per-user AI personalization config.
 * Requirements: 19.39
 */
@Serializable
data class UserAIConfig(
    val userId: String = "",
    val skills: String = "",
    val workflow: String = "",
    val instructions: String = "",
    val rules: String = "",
    val updatedAt: String = ""
)
