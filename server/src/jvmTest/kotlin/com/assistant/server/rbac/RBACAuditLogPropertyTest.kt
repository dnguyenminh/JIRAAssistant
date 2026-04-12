package com.assistant.server.rbac

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 3: RBAC Audit Log Completeness
 *
 * For any role/permission change, RBAC creates an audit log with all required fields:
 * actor_id, target_user_id, action, old_value, new_value, and a valid ISO-8601 timestamp.
 *
 * **Validates: Requirements 7.6, 16.4**
 *
 * Feature: jira-assistant-app, Property 3: RBAC Audit Log Completeness
 */
@OptIn(ExperimentalKotest::class)
class RBACAuditLogPropertyTest {

    private fun arbSafeString(minLength: Int = 1, maxLength: Int = 30): Arb<String> =
        Arb.string(minSize = minLength, maxSize = maxLength, codepoints = Codepoint.alphanumeric())

    private val arbRole: Arb<UserRole> = Arb.enum<UserRole>()
    private val arbPermission: Arb<Permission> = Arb.enum<Permission>()
    private val arbBoolean: Arb<Boolean> = Arb.boolean()

    // ISO-8601 local datetime pattern: YYYY-MM-DDTHH:MM:SS (with optional fractional seconds)
    private val iso8601Regex = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?""")

    @Test
    fun changeRoleCreatesCompleteAuditLog() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbSafeString(5, 20),
                arbSafeString(5, 20),
                arbRole,
                arbRole
            ) { adminId, targetUserId, initialRole, newRole ->
                val auditLogStore = InMemoryAuditLogStore()
                val userStore = InMemoryUserStore()
                val rbacEngine = RBACEngineImpl(userStore, auditLogStore)

                // Set up a target user
                userStore.addUser(
                    User(
                        id = targetUserId,
                        name = "Test User",
                        email = "test@example.com",
                        role = initialRole
                    )
                )

                rbacEngine.changeRole(adminId, targetUserId, newRole)

                if (initialRole != newRole) {
                    val logs = auditLogStore.getAll()
                    assertTrue(logs.isNotEmpty(), "Audit log should have at least one entry after role change")

                    val entry = logs.last()
                    assertEquals(adminId, entry.actorId, "actorId must match admin")
                    assertEquals(targetUserId, entry.targetUserId, "targetUserId must match")
                    assertEquals("CHANGE_ROLE", entry.action, "action must be CHANGE_ROLE")
                    assertEquals(initialRole.name, entry.oldValue, "oldValue must be the old role")
                    assertEquals(newRole.name, entry.newValue, "newValue must be the new role")
                    assertTrue(entry.timestamp.isNotBlank(), "timestamp must not be blank")
                    assertTrue(
                        iso8601Regex.containsMatchIn(entry.timestamp),
                        "timestamp '${entry.timestamp}' must be valid ISO-8601"
                    )
                    assertTrue(entry.tag.isNotBlank(), "tag must not be blank")
                }
            }
        }
    }

    @Test
    fun togglePermissionCreatesCompleteAuditLog() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 25),
                arbSafeString(5, 20),
                arbSafeString(5, 20),
                arbPermission,
                arbBoolean
            ) { adminId, targetUserId, permission, enabled ->
                val auditLogStore = InMemoryAuditLogStore()
                val userStore = InMemoryUserStore()
                val rbacEngine = RBACEngineImpl(userStore, auditLogStore)

                userStore.addUser(
                    User(
                        id = targetUserId,
                        name = "Test User",
                        email = "test@example.com",
                        role = UserRole.READER
                    )
                )

                rbacEngine.togglePermission(adminId, targetUserId, permission, enabled)

                val logs = auditLogStore.getAll()
                assertTrue(logs.isNotEmpty(), "Audit log should have at least one entry after permission toggle")

                val entry = logs.last()
                assertEquals(adminId, entry.actorId, "actorId must match admin")
                assertEquals(targetUserId, entry.targetUserId, "targetUserId must match")
                assertEquals("TOGGLE_PERMISSION", entry.action, "action must be TOGGLE_PERMISSION")
                assertNotNull(entry.oldValue, "oldValue must not be null")
                assertNotNull(entry.newValue, "newValue must not be null")
                assertTrue(entry.timestamp.isNotBlank(), "timestamp must not be blank")
                assertTrue(
                    iso8601Regex.containsMatchIn(entry.timestamp),
                    "timestamp '${entry.timestamp}' must be valid ISO-8601"
                )
                assertTrue(entry.tag.isNotBlank(), "tag must not be blank")
            }
        }
    }
}
