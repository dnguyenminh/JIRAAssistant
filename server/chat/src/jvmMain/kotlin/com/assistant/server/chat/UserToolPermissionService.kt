package com.assistant.server.chat

import com.assistant.chat.ToolPermissionsResponse
import com.assistant.chat.UserToolPermissionRepository
import com.assistant.mcp.McpServerRepository
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Service for per-user MCP tool permissions (enable/disable).
 * Requirements: 3.2, 3.3, 3.4, 3.6, 3.7, 6.1, 6.2
 */
class UserToolPermissionService(
    private val permRepo: UserToolPermissionRepository,
    private val mcpServerRepo: McpServerRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_SEPARATOR = "::"
        private val VALID_VALUES = setOf("enabled", "disabled")
    }

    /** Lấy effective permissions cho user, merge với defaults. Req: 3.2 */
    suspend fun getEffectivePermissions(userId: String): ToolPermissionsResponse {
        val userPerms = loadUserPermissions(userId)
        val defaults = buildDefaults()
        return ToolPermissionsResponse(
            permissions = userPerms,
            defaults = defaults
        )
    }

    /** Kiểm tra tool có enabled cho user không. Req: 6.1, 6.2 */
    suspend fun isEnabled(userId: String, serverId: String, toolName: String): Boolean {
        val perms = loadUserPermissionsSafe(userId)
        val key = "$serverId$KEY_SEPARATOR$toolName"
        return perms[key] != "disabled"
    }

    /** Lấy set of disabled tool keys cho user. Req: 1.3, 6.4 */
    suspend fun getDisabledTools(userId: String): Set<String> {
        val perms = loadUserPermissionsSafe(userId)
        return perms.filterValues { it == "disabled" }.keys
    }

    /** Validate rồi lưu permissions. Req: 3.3, 3.7 */
    suspend fun savePermissions(userId: String, permissions: Map<String, String>): Result<Unit> {
        val validation = validate(permissions)
        if (validation.isFailure) return validation
        return runCatching { permRepo.save(userId, permissions) }
    }

    /** Bulk enable/disable tất cả tools của 1 server. Req: 3.6 */
    suspend fun bulkUpdate(userId: String, serverId: String, action: String): Result<Unit> {
        if (action !in setOf("enable_all", "disable_all")) {
            return Result.failure(IllegalArgumentException("Invalid action: $action"))
        }
        val tools = getToolsForServer(serverId)
        if (tools.isEmpty()) {
            return Result.failure(NoSuchElementException("Server '$serverId' not found or has no tools"))
        }
        return applyBulkAction(userId, serverId, tools, action)
    }

    /** Validate format: key = "serverId::toolName", value = "enabled"|"disabled". Req: 3.7 */
    fun validate(permissions: Map<String, String>): Result<Unit> {
        for ((key, value) in permissions) {
            if (!isValidKey(key)) {
                return Result.failure(
                    IllegalArgumentException("Invalid key format '$key': must be 'serverId::toolName'")
                )
            }
            if (value !in VALID_VALUES) {
                return Result.failure(
                    IllegalArgumentException("Invalid value '$value' for key '$key': must be 'enabled' or 'disabled'")
                )
            }
        }
        return Result.success(Unit)
    }

    // --- Private helpers ---

    private fun isValidKey(key: String): Boolean {
        val parts = key.split(KEY_SEPARATOR)
        return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
    }

    private suspend fun loadUserPermissions(userId: String): Map<String, String> {
        return permRepo.findByUserId(userId) ?: emptyMap()
    }

    /** Load permissions with DB error fallback (all enabled). Req: 6.2 */
    private suspend fun loadUserPermissionsSafe(userId: String): Map<String, String> {
        return try {
            permRepo.findByUserId(userId) ?: emptyMap()
        } catch (e: Exception) {
            logger.warn("Failed to load permissions for user '$userId', fallback to all enabled", e)
            emptyMap()
        }
    }

    /** Build defaults map from mcp_servers.auto_approve. Req: 3.4 */
    private suspend fun buildDefaults(): Map<String, String> {
        val defaults = mutableMapOf<String, String>()
        val servers = mcpServerRepo.getAll()
        for (server in servers) {
            val approved = parseAutoApprove(server.autoApprove)
            for (toolName in approved) {
                defaults["${server.id}$KEY_SEPARATOR$toolName"] = "enabled"
            }
        }
        return defaults
    }

    private fun parseAutoApprove(raw: String): List<String> = try {
        json.decodeFromString<List<String>>(raw)
    } catch (_: Exception) {
        emptyList()
    }

    /** Get tool names for a server (from autoApprove + existing user perms). Req: 3.6 */
    private suspend fun getToolsForServer(serverId: String): List<String> {
        val server = mcpServerRepo.findById(serverId) ?: return emptyList()
        return parseAutoApprove(server.autoApprove).ifEmpty { listOf(server.name) }
    }

    /** Apply bulk action, merging with existing permissions. */
    private suspend fun applyBulkAction(
        userId: String, serverId: String,
        tools: List<String>, action: String
    ): Result<Unit> {
        val value = if (action == "enable_all") "enabled" else "disabled"
        val existing = loadUserPermissions(userId).toMutableMap()
        // Update known tools from autoApprove
        for (toolName in tools) {
            existing["$serverId$KEY_SEPARATOR$toolName"] = value
        }
        // Also update any existing user permissions for this server
        updateExistingServerPerms(existing, serverId, value)
        return runCatching { permRepo.save(userId, existing) }
    }

    private fun updateExistingServerPerms(
        perms: MutableMap<String, String>, serverId: String, value: String
    ) {
        for (key in perms.keys) {
            if (key.startsWith("$serverId$KEY_SEPARATOR")) {
                perms[key] = value
            }
        }
    }
}
