package com.assistant.chat

/**
 * Repository for per-user MCP tool permissions (enable/disable).
 * Requirements: 3.1, 3.2
 */
interface UserToolPermissionRepository {
    suspend fun findByUserId(userId: String): Map<String, String>?
    suspend fun save(userId: String, permissions: Map<String, String>)
    suspend fun delete(userId: String)
}
