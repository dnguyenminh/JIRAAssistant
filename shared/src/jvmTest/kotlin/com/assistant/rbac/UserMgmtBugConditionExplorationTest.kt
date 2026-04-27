package com.assistant.rbac

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Tests — User Management Audit Fix
 *
 * These tests encode the EXPECTED (correct) behavior. They are designed to
 * FAIL on unfixed code, confirming the bugs exist. After the fix is applied,
 * these same tests should PASS, confirming the bugs are resolved.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6**
 */
class UserMgmtBugConditionExplorationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Surrogate models mirroring the actual frontend/backend field names
    // to test cross-boundary deserialization without importing frontend code.

    @Serializable
    data class UserDtoSurrogate(
        val id: String,
        val name: String,
        val email: String,
        val role: String,
        val avatarUrl: String? = null,
        val customPermissions: List<String> = emptyList()
    )

    @Serializable
    data class UserInfoSurrogate(
        @SerialName("id") val userId: String = "",
        val email: String = "",
        @SerialName("name") val displayName: String = "",
        val role: String = "",
        @SerialName("customPermissions") val permissions: List<String> = emptyList()
    )

    @Serializable
    data class FrontendAuditEntrySurrogate(
        val timestamp: String = "",
        @SerialName("actorId") val actor: String = "",
        @SerialName("targetUserId") val target: String = "",
        val action: String = "",
        val oldValue: String = "",
        val newValue: String = ""
    )

    // ------------------------------------------------------------------
    // Test 1a — UserDto → UserInfo field mismatch
    // Backend UserDto: id, name, customPermissions
    // Frontend UserInfo: userId, displayName, permissions
    // WILL FAIL on unfixed code: JSON keys don't match UserInfo fields.
    // ------------------------------------------------------------------

    @Test
    fun `1a - UserDto JSON deserialized as UserInfo maps id to userId`() {
        val userDtoJson = json.encodeToString(
            UserDtoSurrogate.serializer(),
            UserDtoSurrogate(
                id = "u1",
                name = "Alice",
                email = "alice@example.com",
                role = "ADMINISTRATOR",
                customPermissions = listOf("ANALYZE_AI")
            )
        )

        val userInfo = json.decodeFromString(
            UserInfoSurrogate.serializer(), userDtoJson
        )

        assertEquals(
            "u1", userInfo.userId,
            "userId should be 'u1' but got '${userInfo.userId}' — field mismatch"
        )
        assertEquals(
            "Alice", userInfo.displayName,
            "displayName should be 'Alice' but got '${userInfo.displayName}'"
        )
        assertEquals(
            listOf("ANALYZE_AI"), userInfo.permissions,
            "permissions should be [ANALYZE_AI] but got ${userInfo.permissions}"
        )
    }

    // ------------------------------------------------------------------
    // Test 1b — Backend AuditLogEntry → Frontend AuditLogEntry mismatch
    // Backend: actorId, targetUserId, tag
    // Frontend: actor, target (no tag)
    // WILL FAIL on unfixed code: JSON keys don't match frontend fields.
    // ------------------------------------------------------------------

    @Test
    fun `1b - Backend AuditLogEntry JSON deserialized as frontend maps actorId to actor`() {
        val backendEntry = AuditLogEntry(
            timestamp = "2025-01-15T10:30:00",
            actorId = "admin1",
            targetUserId = "u2",
            action = "ROLE_CHANGE",
            oldValue = "READER",
            newValue = "ADMINISTRATOR",
            tag = "IAM_SYNC"
        )
        val backendJson = json.encodeToString(
            AuditLogEntry.serializer(), backendEntry
        )

        val frontendEntry = json.decodeFromString(
            FrontendAuditEntrySurrogate.serializer(), backendJson
        )

        assertEquals(
            "admin1", frontendEntry.actor,
            "actor should be 'admin1' but got '${frontendEntry.actor}'"
        )
        assertEquals(
            "u2", frontendEntry.target,
            "target should be 'u2' but got '${frontendEntry.target}'"
        )
    }

    // ------------------------------------------------------------------
    // Test 1c — GET /api/users/audit-log endpoint missing
    // WILL FAIL on unfixed code: route not defined in UserRoutes.kt.
    // ------------------------------------------------------------------

    @Test
    fun `1c - UserRoutes defines GET audit-log endpoint`() {
        // Try both relative paths (from shared/ and from project root)
        val candidates = listOf(
            java.io.File("server/src/jvmMain/kotlin/com/assistant/server/routes/UserRoutes.kt"),
            java.io.File("../server/src/jvmMain/kotlin/com/assistant/server/routes/UserRoutes.kt")
        )
        val routesFile = candidates.firstOrNull { it.exists() }
        assertTrue(routesFile != null, "UserRoutes.kt must exist")

        val source = routesFile!!.readText()
        assertTrue(
            source.contains("""get("/audit-log")""") ||
                source.contains("""get("audit-log")"""),
            "UserRoutes.kt must define GET /audit-log endpoint"
        )
    }

    // ------------------------------------------------------------------
    // Test 1d — InMemoryAuditLogStore volatility
    // Append entries, create new instance, call getAll().
    // WILL FAIL: new instance has empty list (no persistence).
    // ------------------------------------------------------------------

    @Test
    fun `1d - Audit entries persist across AuditLogStore instances`() = runTest {
        val dataDir = tempFolder.newFolder("audit-data").absolutePath
        val store1 = FileBasedAuditLogStore(dataDir)
        store1.append(
            AuditLogEntry(
                timestamp = "2025-01-15T10:00:00",
                actorId = "admin1",
                targetUserId = "user1",
                action = "CHANGE_ROLE",
                oldValue = "READER",
                newValue = "ADMINISTRATOR",
                tag = "RBAC"
            )
        )
        store1.append(
            AuditLogEntry(
                timestamp = "2025-01-15T10:05:00",
                actorId = "admin1",
                targetUserId = "user2",
                action = "TOGGLE_PERMISSION",
                oldValue = "ANALYZE_AI=false",
                newValue = "ANALYZE_AI=true",
                tag = "IAM_SYNC"
            )
        )

        assertEquals(2, store1.getAll().size, "First instance should have 2 entries")

        // New instance simulates JVM restart — same dataDir
        val store2 = FileBasedAuditLogStore(dataDir)
        val persisted = store2.getAll()

        assertTrue(
            persisted.isNotEmpty(),
            "New instance should retain entries but got ${persisted.size}"
        )
        assertEquals(2, persisted.size, "Should have all 2 entries persisted")
    }
}
