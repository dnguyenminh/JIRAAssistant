package com.assistant.rbac

import com.assistant.auth.UserRole

/**
 * Hardcoded permission matrix defining which permissions each role has.
 *
 * - Administrator: all permissions
 * - Neural_Architect: analysis, KB, test provider, view, sign out
 * - Reader: view-only + sign out
 */
object PermissionMatrix {
    private val matrix: Map<UserRole, Set<Permission>> = mapOf(
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

    fun check(role: UserRole, permission: Permission): Boolean =
        matrix[role]?.contains(permission) ?: false

    fun getPermissions(role: UserRole): Set<Permission> =
        matrix[role] ?: emptySet()
}
