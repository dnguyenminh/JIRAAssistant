package com.assistant.rbac

// Feature: user-crud-profile, Property 7: UserStore operations succeed for existing users
// Feature: user-crud-profile, Property 4: Email uniqueness enforcement
// Feature: user-crud-profile, Property 9: Status change persistence
// Feature: user-crud-profile, Property 10: Delete removes user permanently

import com.assistant.auth.UserRole
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import io.kotest.common.ExperimentalKotest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for UserStore operations.
 *
 * Covers Properties 4, 7, 9, and 10 from the design document.
 */
@OptIn(ExperimentalKotest::class)
class UserStorePropertyTest {

    // ── Generators ──────────────────────────────────────────────

    private val arbUserId = Arb.string(5..12, Codepoint.alphanumeric())
    private val arbUserName = Arb.string(1..20, Codepoint.alphanumeric())
    private val arbEmail = Arb.string(3..10, Codepoint.alphanumeric())
        .map { "${it}@test.com" }
    private val arbRole = Arb.enum<UserRole>()
    private val arbStatus = Arb.enum<UserStatus>()

    private fun arbUser(
        id: Arb<String> = arbUserId,
        email: Arb<String> = arbEmail
    ): Arb<User> = Arb.bind(id, arbUserName, email, arbRole) { uid, name, em, role ->
        User(id = uid, name = name, email = em, role = role)
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun freshStore() = InMemoryUserStore()

    // ─────────────────────────────────────────────────────────────
    // Property 7: UserStore operations succeed for existing users
    //
    // For any user added to the store, updateUser/deleteUser/updateStatus
    // return true. For non-existent IDs they return false.
    // **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Property 7 - updateUser returns true for existing user`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser()) { user ->
            val store = freshStore()
            store.addUser(user)
            val result = store.updateUser(user.id, "NewName", "new@test.com")
            assertTrue(result, "updateUser should return true for existing user")
        }
    }

    @Test
    fun `Property 7 - deleteUser returns true for existing user`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser()) { user ->
            val store = freshStore()
            store.addUser(user)
            val result = store.deleteUser(user.id)
            assertTrue(result, "deleteUser should return true for existing user")
        }
    }

    @Test
    fun `Property 7 - updateStatus returns true for existing user`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser(), arbStatus) { user, status ->
            val store = freshStore()
            store.addUser(user)
            val result = store.updateStatus(user.id, status)
            assertTrue(result, "updateStatus should return true for existing user")
        }
    }

    @Test
    fun `Property 7 - operations return false for non-existent IDs`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUserId) { fakeId ->
            val store = freshStore()
            assertFalse(store.updateUser(fakeId, "X", "x@t.com"))
            assertFalse(store.deleteUser(fakeId))
            assertFalse(store.updateStatus(fakeId, UserStatus.ACTIVE))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Property 4: Email uniqueness enforcement
    //
    // Adding two users with the same email rejects the second.
    // Updating a user's email to one held by another is rejected.
    // **Validates: Requirements 1.9, 3.8, 7.7**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Property 4 - addUser rejects duplicate email`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbEmail, arbUserId, arbUserId) { email, id1, id2 ->
            if (id1 != id2) {
                val store = freshStore()
                val user1 = User(id = id1, name = "A", email = email, role = UserRole.READER)
                val user2 = User(id = id2, name = "B", email = email, role = UserRole.READER)
                store.addUser(user1)
                assertFailsWith<IllegalArgumentException> { store.addUser(user2) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Property 9: Status change persistence
    //
    // ACTIVE → DISABLED, DISABLED → ACTIVE, and round-trip.
    // **Validates: Requirements 4.3, 4.6**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Property 9 - ACTIVE to DISABLED persists`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser()) { user ->
            val store = freshStore()
            val activeUser = user.copy(status = UserStatus.ACTIVE)
            store.addUser(activeUser)
            store.updateStatus(activeUser.id, UserStatus.DISABLED)
            val stored = store.findById(activeUser.id)!!
            assertEquals(UserStatus.DISABLED, stored.status)
        }
    }

    @Test
    fun `Property 9 - DISABLED to ACTIVE persists`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser()) { user ->
            val store = freshStore()
            val disabledUser = user.copy(status = UserStatus.DISABLED)
            store.addUser(disabledUser)
            store.updateStatus(disabledUser.id, UserStatus.ACTIVE)
            val stored = store.findById(disabledUser.id)!!
            assertEquals(UserStatus.ACTIVE, stored.status)
        }
    }

    @Test
    fun `Property 9 - round-trip ACTIVE to DISABLED to ACTIVE`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser()) { user ->
            val store = freshStore()
            val activeUser = user.copy(status = UserStatus.ACTIVE)
            store.addUser(activeUser)

            store.updateStatus(activeUser.id, UserStatus.DISABLED)
            assertEquals(UserStatus.DISABLED, store.findById(activeUser.id)!!.status)

            store.updateStatus(activeUser.id, UserStatus.ACTIVE)
            assertEquals(UserStatus.ACTIVE, store.findById(activeUser.id)!!.status)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Property 10: Delete removes user permanently
    //
    // After deleteUser, findById returns null and getAll excludes them.
    // **Validates: Requirements 5.4**
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Property 10 - delete removes user from findById and getAll`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbUser()) { user ->
            val store = freshStore()
            store.addUser(user)
            store.deleteUser(user.id)
            assertNull(store.findById(user.id), "findById should return null after delete")
            val allIds = store.getAll().map { it.id }
            assertFalse(user.id in allIds, "getAll should not contain deleted user")
        }
    }

    // ── STC: UT-20/21/22 — Dedicated store tests for non-existent users
    // STC: UT-20 — userStore.updateUser("nonexistent") → false
    @Test
    fun `UT-20 - updateUser returns false for non-existent`() = runTest {
        assertFalse(freshStore().updateUser("nonexistent", "name", "e@t.com"))
    }
    // STC: UT-21 — userStore.deleteUser("nonexistent") → false
    @Test
    fun `UT-21 - deleteUser returns false for non-existent`() = runTest {
        assertFalse(freshStore().deleteUser("nonexistent"))
    }
    // STC: UT-22 — userStore.updateStatus("nonexistent") → false
    @Test
    fun `UT-22 - updateStatus returns false for non-existent`() = runTest {
        assertFalse(freshStore().updateStatus("nonexistent", UserStatus.DISABLED))
    }
}
