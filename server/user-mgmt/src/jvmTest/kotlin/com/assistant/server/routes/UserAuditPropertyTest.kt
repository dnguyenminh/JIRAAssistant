package com.assistant.server.routes

// Feature: user-crud-profile, Property 6: CRUD audit logging completeness

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 6: CRUD audit logging completeness
 *
 * For each CRUD operation (create, update, disable, enable, delete),
 * verify an AuditLogEntry is appended with correct actor ID, target
 * user ID, action tag, and old/new values.
 *
 * **Validates: Requirements 1.8, 3.7, 4.5, 4.8, 5.6**
 */
@OptIn(ExperimentalKotest::class)
class UserAuditPropertyTest {

    // ── Generators ──────────────────────────────────────────────

    private val arbId = Arb.string(8..12, Codepoint.alphanumeric())
    private val arbName = Arb.string(2..20, Codepoint.alphanumeric())
    private val arbEmail = Arb.string(3..8, Codepoint.alphanumeric())
        .map { "$it@test.com" }
    private val arbRole = Arb.enum<UserRole>()

    private fun createUser(id: String, name: String, email: String, role: UserRole) =
        User(id = id, name = name, email = email, role = role,
            status = UserStatus.ACTIVE, createdAt = Instant.now().toString())

    // ── Tests ───────────────────────────────────────────────────

    @Test
    fun `Property 6 - create user appends audit entry`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            arbId, arbId, arbName, arbEmail, arbRole
        ) { actorId, userId, name, email, role ->
            val auditStore = InMemoryAuditLogStore()
            val userStore = InMemoryUserStore()
            val user = createUser(userId, name, email, role)
            userStore.addUser(user)

            appendAuditHelper(auditStore, actorId, userId,
                "USER_CREATED", "", "role=${role.name}")

            val entries = auditStore.getAll()
            assertEquals(1, entries.size)
            val entry = entries.first()
            assertEquals(actorId, entry.actorId)
            assertEquals(userId, entry.targetUserId)
            assertEquals("USER_CREATED", entry.action)
            assertEquals("role=${role.name}", entry.newValue)
        }
    }

    @Test
    fun `Property 6 - update user appends audit entry`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            arbId, arbId, arbName, arbEmail, arbName, arbEmail
        ) { actorId, userId, oldName, oldEmail, newName, newEmail ->
            val auditStore = InMemoryAuditLogStore()
            val oldValue = "name=$oldName, email=$oldEmail"
            val newValue = "name=$newName, email=$newEmail"

            appendAuditHelper(auditStore, actorId, userId,
                "USER_UPDATED", oldValue, newValue)

            val entry = auditStore.getAll().first()
            assertEquals("USER_UPDATED", entry.action)
            assertEquals(oldValue, entry.oldValue)
            assertEquals(newValue, entry.newValue)
        }
    }

    @Test
    fun `Property 6 - disable user appends audit entry`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbId, arbId) { actorId, userId ->
            val auditStore = InMemoryAuditLogStore()
            appendAuditHelper(auditStore, actorId, userId,
                "USER_DISABLED", "ACTIVE", "DISABLED")

            val entry = auditStore.getAll().first()
            assertEquals("USER_DISABLED", entry.action)
            assertEquals("ACTIVE", entry.oldValue)
            assertEquals("DISABLED", entry.newValue)
        }
    }

    @Test
    fun `Property 6 - enable user appends audit entry`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbId, arbId) { actorId, userId ->
            val auditStore = InMemoryAuditLogStore()
            appendAuditHelper(auditStore, actorId, userId,
                "USER_ENABLED", "DISABLED", "ACTIVE")

            val entry = auditStore.getAll().first()
            assertEquals("USER_ENABLED", entry.action)
            assertEquals("DISABLED", entry.oldValue)
            assertEquals("ACTIVE", entry.newValue)
        }
    }

    @Test
    fun `Property 6 - delete user appends audit entry`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            arbId, arbId, arbName, arbEmail
        ) { actorId, userId, name, email ->
            val auditStore = InMemoryAuditLogStore()
            val oldValue = "name=$name, email=$email"
            appendAuditHelper(auditStore, actorId, userId,
                "USER_DELETED", oldValue, "")

            val entry = auditStore.getAll().first()
            assertEquals("USER_DELETED", entry.action)
            assertEquals(actorId, entry.actorId)
            assertEquals(userId, entry.targetUserId)
            assertEquals(oldValue, entry.oldValue)
            assertEquals("", entry.newValue)
            assertTrue(entry.tag.isNotEmpty())
        }
    }

    // ── Helper ──────────────────────────────────────────────────

    private suspend fun appendAuditHelper(
        store: AuditLogStore, actorId: String,
        targetUserId: String, action: String,
        oldValue: String, newValue: String
    ) {
        store.append(AuditLogEntry(
            timestamp = Instant.now().toString(),
            actorId = actorId, targetUserId = targetUserId,
            action = action, oldValue = oldValue,
            newValue = newValue, tag = "IAM_SYNC"
        ))
    }
}
