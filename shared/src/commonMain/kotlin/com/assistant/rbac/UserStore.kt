package com.assistant.rbac

/**
 * Interface for user persistence. In-memory for now; backed by SQLDelight in Task 4.
 */
interface UserStore {
    suspend fun getAll(): List<User>
    suspend fun findById(userId: String): User?
    suspend fun updateRole(userId: String, newRole: com.assistant.auth.UserRole): Boolean
    suspend fun updatePermissions(userId: String, permissions: Set<Permission>): Boolean
}
