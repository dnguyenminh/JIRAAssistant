package com.assistant.rbac

// Feature: user-crud-profile, Property 3: User model serialization round-trip

import com.assistant.auth.UserRole
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.kotest.common.ExperimentalKotest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 3: User model serialization round-trip
 *
 * For any valid User instance (with any combination of status, role,
 * permissions, and createdAt), serializing to JSON and then deserializing
 * back SHALL produce an equivalent User object with all fields preserved.
 *
 * **Validates: Requirements 6.6**
 */
@OptIn(ExperimentalKotest::class)
class UserSerializationPropertyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Generators ──────────────────────────────────────────────

    private val arbUserId = Arb.string(5..12, Codepoint.alphanumeric())
    private val arbUserName = Arb.string(1..30, Codepoint.alphanumeric())
    private val arbEmail = Arb.string(3..10, Codepoint.alphanumeric()).map { "$it@test.com" }
    private val arbRole = Arb.enum<UserRole>()
    private val arbStatus = Arb.enum<UserStatus>()
    private val arbPermissions = Arb.subsequence(Permission.entries.toList())
        .map { it.toSet() }
    private val arbCreatedAt = Arb.element(
        "2025-01-15T10:30:00Z",
        "2024-06-01T00:00:00Z",
        "",
        "2023-12-31T23:59:59Z"
    )
    private val arbAvatarUrl = Arb.choice(
        Arb.constant(null as String?),
        Arb.string(5..20, Codepoint.alphanumeric()).map { "https://avatar.example.com/$it" }
    )

    private val arbUser = Arb.bind(
        arbUserId, arbUserName, arbEmail, arbRole, arbAvatarUrl, arbPermissions, arbStatus, arbCreatedAt
    ) { id, name, email, role, avatar, perms, status, createdAt ->
        User(
            id = id, name = name, email = email, role = role,
            avatarUrl = avatar, customPermissions = perms,
            status = status, createdAt = createdAt
        )
    }

    // ── Property Test ───────────────────────────────────────────

    @Test
    fun `Property 3 - serialize then deserialize produces equivalent User`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser) { user ->
            val jsonStr = json.encodeToString(user)
            val restored = json.decodeFromString<User>(jsonStr)
            assertEquals(user, restored, "Round-trip failed for user: $user")
        }
    }

    // ── STC: UT-18 — Missing status field defaults to ACTIVE ────

    @Test
    fun `UT-18 - deserialize JSON without status defaults to ACTIVE`() {
        val jsonStr = """{"id":"x","name":"Test","email":"t@t.com","role":"READER","customPermissions":[]}"""
        val user = json.decodeFromString<User>(jsonStr)
        assertEquals(UserStatus.ACTIVE, user.status,
            "Missing status field should default to ACTIVE")
    }

    // ── STC: UT-19 — Missing createdAt field defaults to empty ──

    @Test
    fun `UT-19 - deserialize JSON without createdAt defaults to empty`() {
        val jsonStr = """{"id":"x","name":"Test","email":"t@t.com","role":"READER","status":"ACTIVE"}"""
        val user = json.decodeFromString<User>(jsonStr)
        assertEquals("", user.createdAt,
            "Missing createdAt field should default to empty string")
    }
}
