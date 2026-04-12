package com.assistant.chat

/**
 * Repository for per-user AI personalization config.
 * Requirements: 19.39, 19.40
 */
interface UserAIConfigRepository {
    suspend fun findByUserId(userId: String): UserAIConfig?
    suspend fun save(config: UserAIConfig)
}
