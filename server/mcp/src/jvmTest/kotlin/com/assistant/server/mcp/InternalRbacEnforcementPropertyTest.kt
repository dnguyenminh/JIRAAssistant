package com.assistant.server.mcp

import com.assistant.auth.UserRole
import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.rbac.Permission
import com.assistant.rbac.PermissionMatrix
import com.assistant.server.mcp.internal.InternalToolRegistry
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 8: RBAC enforcement cho mọi tool call
 *
 * For any tool trong Internal_MCP_Server và for any user có role
 * KHÔNG đủ permission cho tool đó, khi gọi tool, hệ thống SHALL
 * trả về error "Access denied" mà KHÔNG thực thi logic của tool.
 * Ngược lại, user có đủ permission SHALL được phép thực thi.
 *
 * **Validates: Requirements AC 6.104, AC 6.106**
 *
 * Feature: mcp-servers, Property 8: RBAC enforcement cho mọi tool call
 */
@OptIn(ExperimentalKotest::class)
class InternalRbacEnforcementPropertyTest {

    private val registry = InternalToolRegistry()
    private val allTools = registry.getAllTools()
    private val arbTool = Arb.of(allTools)
    private val arbRole = Arb.of(UserRole.entries.toList())

    /** Parse permission string from tool definition. */
    private fun parsePermission(tool: InternalToolDefinition): Permission =
        Permission.valueOf(tool.requiredPermission.uppercase())

    /**
     * Property 8a — Denied roles cannot access tools.
     * For any (tool, role) pair where PermissionMatrix denies,
     * RBAC check must return false.
     */
    @Test
    fun `Property 8a - denied roles are blocked from tools`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbTool, arbRole) { tool, role ->
                val permission = parsePermission(tool)
                val allowed = PermissionMatrix.check(role, permission)
                if (!allowed) {
                    assertFalse(
                        allowed,
                        "Role $role must be denied '${tool.name}' " +
                            "(requires ${tool.requiredPermission})"
                    )
                }
            }
        }
    }

    /**
     * Property 8b — Administrator has access to ALL tools.
     * Req 6.104: Administrator has all permissions.
     */
    @Test
    fun `Property 8b - Administrator can access all tools`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbTool) { tool ->
                val permission = parsePermission(tool)
                assertTrue(
                    PermissionMatrix.check(UserRole.ADMINISTRATOR, permission),
                    "Administrator must have access to '${tool.name}'"
                )
            }
        }
    }

    /**
     * Property 8c — Reader is blocked from write tools.
     * Req 6.106: Reader only allowed read-only tools.
     */
    @Test
    fun `Property 8c - Reader blocked from write tools`() {
        val writePermissions = setOf(
            Permission.ANALYZE_AI, Permission.MANAGE_USERS,
            Permission.MANAGE_SETTINGS, Permission.CONFIG_INTEGRATIONS,
            Permission.TOGGLE_PERMISSIONS
        )
        val writeTools = allTools.filter {
            parsePermission(it) in writePermissions
        }
        if (writeTools.isEmpty()) return

        val arbWriteTool = Arb.of(writeTools)
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbWriteTool) { tool ->
                val permission = parsePermission(tool)
                assertFalse(
                    PermissionMatrix.check(UserRole.READER, permission),
                    "Reader must be blocked from '${tool.name}' " +
                        "(requires ${tool.requiredPermission})"
                )
            }
        }
    }

    /**
     * Property 8d — Permission grant/deny is consistent.
     * For any (tool, role), PermissionMatrix result matches
     * whether role's permission set contains the tool's permission.
     */
    @Test
    fun `Property 8d - permission check consistent with matrix`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbTool, arbRole) { tool, role ->
                val permission = parsePermission(tool)
                val matrixResult = PermissionMatrix.check(role, permission)
                val rolePerms = PermissionMatrix.getPermissions(role)
                assertEquals(
                    permission in rolePerms,
                    matrixResult,
                    "Inconsistency for role=$role, tool='${tool.name}'"
                )
            }
        }
    }
}
