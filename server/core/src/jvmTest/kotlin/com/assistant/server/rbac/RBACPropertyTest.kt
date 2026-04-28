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
 * Property 2: RBAC Permission Matrix Enforcement
 *
 * For any (UserRole, Permission) pair, hasPermission(role, permission) SHALL return true
 * if and only if the permission is in the defined set for that role in the permission matrix.
 * Specifically: Reader lacks ANALYZE_AI, RE_ANALYZE, CONFIG_INTEGRATIONS, TEST_PROVIDER,
 * MANAGE_USERS, TOGGLE_PERMISSIONS; Neural_Architect lacks CONFIG_INTEGRATIONS, MANAGE_USERS,
 * TOGGLE_PERMISSIONS.
 *
 * **Validates: Requirements 7.2, 7.8, 11.1, 11.2, 11.3**
 *
 * Feature: jira-assistant-app, Property 2: RBAC Permission Matrix Enforcement
 */
@OptIn(ExperimentalKotest::class)
class RBACPermissionMatrixPropertyTest {

    private val auditLogStore = InMemoryAuditLogStore()
    private val userStore = InMemoryUserStore()
    private val rbacEngine: RBACEngine = RBACEngineImpl(userStore, auditLogStore)

    private val arbRole: Arb<UserRole> = Arb.enum<UserRole>()
    private val arbPermission: Arb<Permission> = Arb.enum<Permission>()

    // Expected permission sets per role (ground truth)
    private val expectedPermissions = mapOf(
        UserRole.ADMINISTRATOR to Permission.entries.toSet(),
        UserRole.NEURAL_ARCHITECT to setOf(
            Permission.VIEW_DASHBOARD, Permission.VIEW_GRAPH, Permission.VIEW_ANALYSIS,
            Permission.ANALYZE_AI, Permission.VIEW_KB, Permission.RE_ANALYZE,
            Permission.TEST_PROVIDER, Permission.SIGN_OUT
        ),
        UserRole.READER to setOf(
            Permission.VIEW_DASHBOARD, Permission.VIEW_GRAPH, Permission.VIEW_ANALYSIS,
            Permission.VIEW_KB, Permission.SIGN_OUT
        )
    )

    @Test
    fun hasPermissionMatchesDefinedMatrix() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbRole, arbPermission) { role, permission ->
                val result = rbacEngine.hasPermission(role, permission)
                val expected = expectedPermissions[role]!!.contains(permission)
                assertEquals(
                    expected, result,
                    "hasPermission($role, $permission) should be $expected but was $result"
                )
            }
        }
    }

    @Test
    fun getPermissionsReturnsExactSetForRole() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbRole) { role ->
                val permissions = rbacEngine.getPermissions(role)
                assertEquals(
                    expectedPermissions[role], permissions,
                    "getPermissions($role) should match expected set"
                )
            }
        }
    }

    @Test
    fun readerLacksRestrictedPermissions() {
        val restrictedForReader = setOf(
            Permission.ANALYZE_AI, Permission.RE_ANALYZE,
            Permission.CONFIG_INTEGRATIONS, Permission.TEST_PROVIDER,
            Permission.MANAGE_USERS, Permission.TOGGLE_PERMISSIONS
        )
        for (permission in restrictedForReader) {
            assertFalse(
                rbacEngine.hasPermission(UserRole.READER, permission),
                "Reader should NOT have $permission"
            )
        }
    }

    @Test
    fun neuralArchitectLacksAdminOnlyPermissions() {
        val restrictedForArchitect = setOf(
            Permission.CONFIG_INTEGRATIONS,
            Permission.MANAGE_USERS,
            Permission.TOGGLE_PERMISSIONS
        )
        for (permission in restrictedForArchitect) {
            assertFalse(
                rbacEngine.hasPermission(UserRole.NEURAL_ARCHITECT, permission),
                "Neural_Architect should NOT have $permission"
            )
        }
    }

    @Test
    fun administratorHasAllPermissions() {
        for (permission in Permission.entries) {
            assertTrue(
                rbacEngine.hasPermission(UserRole.ADMINISTRATOR, permission),
                "Administrator should have $permission"
            )
        }
    }
}
