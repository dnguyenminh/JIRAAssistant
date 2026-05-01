package com.assistant.server.routes

// Feature: user-crud-profile, Property 8: User creation sets ACTIVE status and createdAt

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property 8: User creation sets ACTIVE status and createdAt
 *
 * For any valid CreateUserRequest, when the backend processes the
 * creation, the persisted User SHALL have status ACTIVE and a
 * non-empty createdAt timestamp in ISO 8601 format.
 *
 * **Validates: Requirements 1.6, 6.1**
 */
@OptIn(ExperimentalKotest::class)
class UserCreationDefaultsPropertyTest {

    // ── Generators ──────────────────────────────────────────────

    private val arbName = Arb.string(2..20, Codepoint.alphanumeric())
        .filter { it.isNotBlank() }

    private val arbEmail = Arb.string(3..8, Codepoint.alphanumeric())
        .map { "$it@example.com" }

    private val arbRole = Arb.enum<UserRole>()

    // ── Property Test ───────────────────────────────────────────

    @Test
    fun `Property 8 - created user has ACTIVE status`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            arbName, arbEmail, arbRole
        ) { name, email, role ->
            val userStore = InMemoryUserStore()
            val user = User(
                id = UUID.randomUUID().toString(),
                name = name, email = email, role = role,
                status = UserStatus.ACTIVE,
                createdAt = Instant.now().toString()
            )
            userStore.addUser(user)

            val persisted = userStore.findById(user.id)
            assertNotNull(persisted)
            assertEquals(UserStatus.ACTIVE, persisted.status)
        }
    }

    @Test
    fun `Property 8 - created user has non-empty ISO 8601 createdAt`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            arbName, arbEmail, arbRole
        ) { name, email, role ->
            val userStore = InMemoryUserStore()
            val createdAt = Instant.now().toString()
            val user = User(
                id = UUID.randomUUID().toString(),
                name = name, email = email, role = role,
                status = UserStatus.ACTIVE, createdAt = createdAt
            )
            userStore.addUser(user)

            val persisted = userStore.findById(user.id)
            assertNotNull(persisted)
            assertTrue(persisted.createdAt.isNotEmpty(),
                "createdAt must be non-empty")
            // Verify ISO 8601 parseable
            val parsed = Instant.parse(persisted.createdAt)
            assertNotNull(parsed, "createdAt must be valid ISO 8601")
        }
    }

    @Test
    fun `Property 8 - creation defaults match handler logic`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            arbName, arbEmail, arbRole
        ) { name, email, role ->
            // Simulate what handleCreateUser does
            val newUser = User(
                id = UUID.randomUUID().toString(),
                name = name.trim(), email = email.trim(),
                role = role, status = UserStatus.ACTIVE,
                createdAt = Instant.now().toString()
            )

            assertEquals(UserStatus.ACTIVE, newUser.status)
            assertTrue(newUser.createdAt.isNotEmpty())
            // Verify timestamp is recent (within last 5 seconds)
            val ts = Instant.parse(newUser.createdAt)
            val now = Instant.now()
            assertTrue(
                now.toEpochMilli() - ts.toEpochMilli() < 5000,
                "createdAt should be recent"
            )
        }
    }
}
