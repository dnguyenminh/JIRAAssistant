package com.assistant.rbac

import com.assistant.auth.UserRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory user store. Will be replaced by SQLDelight-backed implementation in Task 4.
 */
class InMemoryUserStore : UserStore {
    private val users = mutableMapOf<String, User>()
    private val mutex = Mutex()

    suspend fun addUser(user: User) {
        mutex.withLock { users[user.id] = user }
    }

    override suspend fun getAll(): List<User> {
        mutex.withLock { return users.values.toList() }
    }

    override suspend fun findById(userId: String): User? {
        mutex.withLock { return users[userId] }
    }

    override suspend fun updateRole(userId: String, newRole: UserRole): Boolean {
        mutex.withLock {
            val user = users[userId] ?: return false
            users[userId] = user.copy(role = newRole)
            return true
        }
    }

    override suspend fun updatePermissions(userId: String, permissions: Set<Permission>): Boolean {
        mutex.withLock {
            val user = users[userId] ?: return false
            users[userId] = user.copy(customPermissions = permissions)
            return true
        }
    }
}
