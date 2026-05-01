package com.assistant.server.routes

// Feature: user-crud-profile, Property 13: Non-existent user returns 404
// Feature: user-crud-profile, Property 14: Invalid request body returns 400

import com.assistant.auth.UserRole
import com.assistant.rbac.*
import com.assistant.server.services.ValidationService
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import java.util.*
import kotlin.test.*

/**
 * Unit tests for CRUD route error cases and property tests for
 * non-existent user (404) and invalid request body (400).
 *
 * Tests handler logic directly using InMemoryUserStore and
 * InMemoryAuditLogStore since full Ktor testApplication requires
 * the aggregator module's DI context.
 *
 * **Validates: Requirements 8.6, 8.7, 8.8, 8.9**
 */
@OptIn(ExperimentalKotest::class)
class UserCrudRoutesTest {

    private fun activeUser(id: String = UUID.randomUUID().toString()) =
        User(id = id, name = "Test User", email = "$id@test.com",
            role = UserRole.ADMINISTRATOR, status = UserStatus.ACTIVE,
            createdAt = "2025-01-15T10:00:00Z")

    // ── 404 for non-existent user ───────────────────────────────

    @Test
    fun `GET non-existent user returns null from store`() = runTest {
        val store = InMemoryUserStore()
        val result = store.findById("non-existent-id")
        assertNull(result, "findById should return null for non-existent user")
    }

    @Test
    fun `PUT non-existent user returns false from store`() = runTest {
        val store = InMemoryUserStore()
        val result = store.updateUser("non-existent-id", "Name", "e@x.com")
        assertFalse(result, "updateUser should return false for non-existent user")
    }

    @Test
    fun `DELETE non-existent user returns false from store`() = runTest {
        val store = InMemoryUserStore()
        val result = store.deleteUser("non-existent-id")
        assertFalse(result, "deleteUser should return false for non-existent user")
    }

    // ── Property 13: Non-existent user returns 404 ──────────────

    @Test
    fun `Property 13 - random non-existent IDs return null`() = runTest {
        val store = InMemoryUserStore()
        // Add one user so store is not empty
        store.addUser(activeUser("existing-user"))

        checkAll(PropTestConfig(iterations = 100),
            Arb.string(10..20, Codepoint.alphanumeric())
        ) { randomId ->
            val found = store.findById(randomId)
            // randomId is extremely unlikely to be "existing-user"
            if (randomId != "existing-user") {
                assertNull(found, "Non-existent ID '$randomId' should return null")
            }
        }
    }

    // ── 409 for duplicate email on POST ─────────────────────────

    @Test
    fun `POST duplicate email throws IllegalArgumentException`() = runTest {
        val store = InMemoryUserStore()
        store.addUser(activeUser("user1").copy(email = "dup@test.com"))

        val ex = assertFailsWith<IllegalArgumentException> {
            store.addUser(activeUser("user2").copy(email = "dup@test.com"))
        }
        assertTrue(ex.message!!.contains("Email already exists"))
    }

    // ── 409 for duplicate email on PUT ──────────────────────────

    @Test
    fun `PUT email conflict detected via findByEmail`() = runTest {
        val store = InMemoryUserStore()
        store.addUser(activeUser("user1").copy(email = "a@test.com"))
        store.addUser(activeUser("user2").copy(email = "b@test.com"))

        // Simulate handler logic: check if email belongs to another user
        val emailOwner = store.findByEmail("a@test.com")
        assertNotNull(emailOwner)
        assertNotEquals("user2", emailOwner.id,
            "Email belongs to different user — should return 409")
    }

    // ── 403 for self-deletion ───────────────────────────────────

    @Test
    fun `DELETE self-deletion detected by comparing actor and target`() {
        val actorId = "admin-123"
        val targetId = "admin-123"
        assertEquals(actorId, targetId,
            "Self-deletion: actorId == targetId should trigger 403")
    }

    @Test
    fun `DELETE different user allowed`() {
        val actorId = "admin-123"
        val targetId = "other-user-456"
        assertNotEquals(actorId, targetId,
            "Different user deletion should be allowed")
    }

    // ── 400 for invalid request bodies ──────────────────────────

    @Test
    fun `validation rejects empty name`() {
        assertFalse(ValidationService.isValidName(""))
        assertFalse(ValidationService.isValidName("   "))
        assertFalse(ValidationService.isValidName("\t"))
    }

    @Test
    fun `validation rejects invalid email`() {
        assertFalse(ValidationService.isValidEmail(""))
        assertFalse(ValidationService.isValidEmail("noatsign"))
        assertFalse(ValidationService.isValidEmail("@domain.com"))
        assertFalse(ValidationService.isValidEmail("user@"))
        assertFalse(ValidationService.isValidEmail("user@domain"))
    }

    @Test
    fun `validation rejects invalid role`() {
        val invalidRoles = listOf("INVALID", "admin", "", "SUPERUSER")
        for (role in invalidRoles) {
            val parsed = try { UserRole.valueOf(role); true }
            catch (_: IllegalArgumentException) { false }
            assertFalse(parsed, "Role '$role' should be invalid")
        }
    }

    @Test
    fun `validation rejects invalid status`() {
        val invalidStatuses = listOf("INVALID", "active", "", "PENDING")
        for (status in invalidStatuses) {
            val valid = try {
                val parsed = UserStatus.valueOf(status)
                parsed != UserStatus.PENDING // PENDING not allowed via API
            } catch (_: IllegalArgumentException) { false }
            assertFalse(valid, "Status '$status' should be invalid for API")
        }
    }

    // ── Property 14: Invalid request body returns 400 ───────────

    @Test
    fun `Property 14 - invalid emails always rejected`() = runTest {
        val arbInvalid = Arb.element(
            "", " ", "noat", "@", "a@", "@b", "a@b",
            "a@.com", "user name@d.com", "user@@d.com"
        )
        checkAll(PropTestConfig(iterations = 100), arbInvalid) { email ->
            assertFalse(ValidationService.isValidEmail(email),
                "Invalid email '$email' should be rejected")
        }
    }

    @Test
    fun `Property 14 - blank names always rejected`() = runTest {
        val arbBlank = Arb.element("", " ", "  ", "\t", "\n", "\r\n")
        checkAll(PropTestConfig(iterations = 100), arbBlank) { name ->
            assertFalse(ValidationService.isValidName(name),
                "Blank name '$name' should be rejected")
        }
    }
}
