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

    override suspend fun addUser(user: User) {
        mutex.withLock {
            val existing = users.values.firstOrNull { it.email == user.email }
            if (existing != null && existing.id != user.id) {
                throw IllegalArgumentException("Email already exists: ${user.email}")
            }
            users[user.id] = user
        }
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

    override suspend fun updateUser(userId: String, name: String, email: String): Boolean {
        mutex.withLock {
            val user = users[userId] ?: return false
            users[userId] = user.copy(name = name, email = email)
            return true
        }
    }

    override suspend fun deleteUser(userId: String): Boolean {
        mutex.withLock { return users.remove(userId) != null }
    }

    override suspend fun updateStatus(userId: String, status: UserStatus): Boolean {
        mutex.withLock {
            val user = users[userId] ?: return false
            users[userId] = user.copy(status = status)
            return true
        }
    }

    override suspend fun findByEmail(email: String): User? {
        mutex.withLock { return users.values.firstOrNull { it.email == email } }
    }
}
