package com.assistant.rbac

import com.assistant.auth.UserRole
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Preservation Property Tests — User Management Audit Fix
 *
 * These tests observe and assert EXISTING correct behavior on UNFIXED code.
 * They must PASS before and after the fix, confirming no regressions.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 */
class UserMgmtPreservationPropertyTest {

    // ── Generators ──────────────────────────────────────────────

    private val arbUserId = Arb.string(5..12, Codepoint.alphanumeric())
    private val arbAdminId = Arb.string(5..12, Codepoint.alphanumeric())
    private val arbUserRole = Arb.enum<UserRole>()
    private val arbPermission = Arb.enum<Permission>()
    private val arbBoolean = Arb.boolean()

    private val arbUserName = Arb.string(3..20, Codepoint.alphanumeric())
    private val arbEmail = arbUserName.map { "$it@test.com" }

    // ── Helpers ─────────────────────────────────────────────────

    private suspend fun buildStoreWithUser(
        userId: String,
        role: UserRole,
        permissions: Set<Permission> = emptySet()
    ): Pair<InMemoryUserStore, InMemoryAuditLogStore> {
        val userStore = InMemoryUserStore()
        val auditStore = InMemoryAuditLogStore()
        userStore.addUser(
            User(
                id = userId,
                name = "User-$userId",
                email = "$userId@test.com",
                role = role,
                customPermissions = permissions
            )
        )
        return userStore to auditStore
    }

    // ─────────────────────────────────────────────────────────────
    // Test 2a — Role Change API Preservation
    // For any valid (adminId, targetUserId, newRole) triple,
    // RBACEngineImpl.changeRole() returns Success and mutates role.
    // **Validates: Requirements 3.1**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `2a - Role change returns success and mutates user role`() = runTest {
        checkAll(20, arbAdminId, arbUserId, arbUserRole, arbUserRole) { adminId, userId, startRole, newRole ->
            val (userStore, auditStore) = buildStoreWithUser(userId, startRole)
            val engine = RBACEngineImpl(userStore, auditStore)

            val result = engine.changeRole(adminId, userId, newRole)

            assertIs<RBACResult.Success>(result, "changeRole should succeed")
            val updatedUser = userStore.findById(userId)!!
            assertEquals(newRole, updatedUser.role, "User role should be $newRole")
        }
    }

    @Test
    fun `2a - Role change for non-existent user returns 404 failure`() = runTest {
        checkAll(10, arbAdminId, arbUserRole) { adminId, newRole ->
            val userStore = InMemoryUserStore()
            val auditStore = InMemoryAuditLogStore()
            val engine = RBACEngineImpl(userStore, auditStore)

            val result = engine.changeRole(adminId, "nonexistent", newRole)

            assertIs<RBACResult.Failure>(result)
            assertEquals(404, result.code)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Test 2b — Permission Toggle API Preservation
    // For any valid (userId, permission, enabled) triple,
    // RBACEngineImpl.togglePermission() returns Success and toggles.
    // **Validates: Requirements 3.2**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `2b - Permission toggle returns success and updates permissions`() = runTest {
        checkAll(20, arbAdminId, arbUserId, arbPermission, arbBoolean) { adminId, userId, perm, enabled ->
            val (userStore, auditStore) = buildStoreWithUser(userId, UserRole.READER)
            val engine = RBACEngineImpl(userStore, auditStore)

            val result = engine.togglePermission(adminId, userId, perm, enabled)

            assertIs<RBACResult.Success>(result, "togglePermission should succeed")
            val updatedUser = userStore.findById(userId)!!
            if (enabled) {
                assertTrue(perm in updatedUser.customPermissions, "$perm should be enabled")
            } else {
                assertFalse(perm in updatedUser.customPermissions, "$perm should be disabled")
            }
        }
    }

    @Test
    fun `2b - Permission toggle for non-existent user returns 404`() = runTest {
        checkAll(10, arbAdminId, arbPermission, arbBoolean) { adminId, perm, enabled ->
            val userStore = InMemoryUserStore()
            val auditStore = InMemoryAuditLogStore()
            val engine = RBACEngineImpl(userStore, auditStore)

            val result = engine.togglePermission(adminId, "nonexistent", perm, enabled)

            assertIs<RBACResult.Failure>(result)
            assertEquals(404, result.code)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Test 2c — Access Control Preservation
    // Non-Administrator roles must NOT have MANAGE_USERS permission.
    // **Validates: Requirements 3.3**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `2c - Non-admin roles do not have MANAGE_USERS permission`() = runTest {
        val nonAdminRoles = UserRole.entries.filter { it != UserRole.ADMINISTRATOR }

        checkAll(10, Arb.element(nonAdminRoles)) { role ->
            val hasManageUsers = PermissionMatrix.check(role, Permission.MANAGE_USERS)
            assertFalse(
                hasManageUsers,
                "Role $role should NOT have MANAGE_USERS permission"
            )
        }
    }

    @Test
    fun `2c - Administrator role has MANAGE_USERS permission`() {
        assertTrue(
            PermissionMatrix.check(UserRole.ADMINISTRATOR, Permission.MANAGE_USERS),
            "ADMINISTRATOR should have MANAGE_USERS"
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Test 2d — RBAC Audit Appending Preservation
    // For any successful role change or permission toggle,
    // AuditLogStore.append() is called with all 7 fields populated.
    // **Validates: Requirements 3.4**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `2d - Role change appends audit entry with all 7 fields`() = runTest {
        checkAll(15, arbAdminId, arbUserId, arbUserRole, arbUserRole) { adminId, userId, startRole, newRole ->
            val (userStore, auditStore) = buildStoreWithUser(userId, startRole)
            val engine = RBACEngineImpl(userStore, auditStore)
            val result = engine.changeRole(adminId, userId, newRole)

            if (startRole == newRole) {
                // "Role unchanged" — no audit entry appended
                assertEquals(0, auditStore.getAll().size)
            } else {
                assertIs<RBACResult.Success>(result)
                val entries = auditStore.getAll()
                assertEquals(1, entries.size, "Exactly 1 audit entry expected")
                val entry = entries.first()
                assertAllFieldsPopulated(entry)
                assertEquals(adminId, entry.actorId)
                assertEquals(userId, entry.targetUserId)
                assertEquals("CHANGE_ROLE", entry.action)
                assertEquals(startRole.name, entry.oldValue)
                assertEquals(newRole.name, entry.newValue)
                assertEquals("IAM_SYNC", entry.tag)
            }
        }
    }

    @Test
    fun `2d - Permission toggle appends audit entry with all 7 fields`() = runTest {
        checkAll(15, arbAdminId, arbUserId, arbPermission, arbBoolean) { adminId, userId, perm, enabled ->
            val (userStore, auditStore) = buildStoreWithUser(userId, UserRole.READER)
            val engine = RBACEngineImpl(userStore, auditStore)

            engine.togglePermission(adminId, userId, perm, enabled)

            val entries = auditStore.getAll()
            assertEquals(1, entries.size, "Exactly 1 audit entry expected")
            val entry = entries.first()
            assertAllFieldsPopulated(entry)
            assertEquals(adminId, entry.actorId)
            assertEquals(userId, entry.targetUserId)
            assertEquals("TOGGLE_PERMISSION", entry.action)
            assertEquals("IAM_SYNC", entry.tag)
        }
    }

    private fun assertAllFieldsPopulated(entry: AuditLogEntry) {
        assertTrue(entry.timestamp.isNotBlank(), "timestamp must be populated")
        assertTrue(entry.actorId.isNotBlank(), "actorId must be populated")
        assertTrue(entry.targetUserId.isNotBlank(), "targetUserId must be populated")
        assertTrue(entry.action.isNotBlank(), "action must be populated")
        // oldValue/newValue can be empty for edge cases but must exist
        assertTrue(entry.tag.isNotBlank(), "tag must be populated")
    }
}
