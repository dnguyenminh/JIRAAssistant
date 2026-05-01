package com.assistant.server.routes

// Feature: user-crud-profile, Property 5: UserDto contains all required fields

import com.assistant.auth.UserRole
import com.assistant.rbac.Permission
import com.assistant.rbac.User
import com.assistant.rbac.UserStatus
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import io.kotest.common.ExperimentalKotest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property 5: UserDto contains all required fields
 *
 * For any valid User object, converting to UserDto SHALL produce a DTO
 * that includes non-null values for id, name, email, role, status, and
 * createdAt. The status and createdAt fields SHALL always be present
 * in the serialized output.
 *
 * **Validates: Requirements 2.3, 6.3**
 */
@OptIn(ExperimentalKotest::class)
class UserDtoCompletenessPropertyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Generators ──────────────────────────────────────────────

    private val arbUserId = Arb.string(5..12, Codepoint.alphanumeric())
    private val arbUserName = Arb.string(1..30, Codepoint.alphanumeric())
    private val arbEmail = Arb.string(3..10, Codepoint.alphanumeric())
        .map { "$it@test.com" }
    private val arbRole = Arb.enum<UserRole>()
    private val arbStatus = Arb.enum<UserStatus>()
    private val arbPermissions = Arb.subsequence(Permission.entries.toList())
        .map { it.toSet() }
    private val arbCreatedAt = Arb.element(
        "2025-01-15T10:30:00Z", "2024-06-01T00:00:00Z",
        "", "2023-12-31T23:59:59Z"
    )
    private val arbAvatarUrl: Arb<String?> = Arb.choice(
        Arb.constant(null as String?),
        Arb.string(5..20, Codepoint.alphanumeric())
            .map { "https://avatar.example.com/$it" }
    )

    private val arbUser: Arb<User> = Arb.bind(
        arbUserId, arbUserName, arbEmail, arbRole,
        arbAvatarUrl, arbPermissions, arbStatus, arbCreatedAt
    ) { id, name, email, role, avatar, perms, status, createdAt ->
        User(
            id = id, name = name, email = email, role = role,
            avatarUrl = avatar, customPermissions = perms,
            status = status, createdAt = createdAt
        )
    }

    // ── Property Test ───────────────────────────────────────────

    @Test
    fun `Property 5 - UserDto contains all required fields`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser) { user ->
            val dto = user.toDto()

            // Verify non-null fields in DTO object
            assertNotNull(dto.id, "id must be non-null")
            assertNotNull(dto.name, "name must be non-null")
            assertNotNull(dto.email, "email must be non-null")
            assertNotNull(dto.role, "role must be non-null")
            assertNotNull(dto.status, "status must be non-null")
            assertNotNull(dto.createdAt, "createdAt must be non-null")

            // Verify serialized JSON contains all required keys
            val jsonStr = json.encodeToString(dto)
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
            val requiredKeys = listOf(
                "id", "name", "email", "role", "status", "createdAt"
            )
            for (key in requiredKeys) {
                assertTrue(
                    jsonObj.containsKey(key),
                    "Serialized JSON must contain '$key'"
                )
            }
        }
    }
}
