package com.assistant.server.chat

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 4: Default = all enabled
 *
 * For any user with no saved record, all tools SHALL have status enabled.
 *
 * **Validates: Requirements 3.4, 6.2**
 *
 * Feature: per-user-tool-permissions, Property 4
 */
@OptIn(ExperimentalKotest::class)
class DefaultAllEnabledPropertyTest {

    private lateinit var permRepo: InMemoryPermissionRepo
    private lateinit var service: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        permRepo = InMemoryPermissionRepo()
        service = UserToolPermissionService(
            permRepo = permRepo,
            mcpServerRepo = InMemoryMcpServerRepo()
        )
    }

    /** Arb for random user IDs: 3-20 alphanumeric chars. */
    private val arbUserId: Arb<String> =
        Arb.string(3..20, Codepoint.az())

    /** Arb for serverId segment: 2-12 lowercase alpha chars. */
    private val arbServerId: Arb<String> =
        Arb.string(2..12, Codepoint.az())

    /** Arb for toolName segment: 2-15 lowercase alpha chars. */
    private val arbToolName: Arb<String> =
        Arb.string(2..15, Codepoint.az())

    /**
     * Property 4 — isEnabled returns true for any tool
     * when user has no saved permissions record.
     */
    @Test
    fun `Property 4 - isEnabled returns true for unknown user`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId, arbServerId, arbToolName
            ) { userId, serverId, toolName ->
                val result = service.isEnabled(userId, serverId, toolName)
                assertTrue(result, "isEnabled should be true for user '$userId', tool '$serverId::$toolName'")
            }
        }
    }

    /**
     * Property 4 — getDisabledTools returns empty set
     * when user has no saved permissions record.
     */
    @Test
    fun `Property 4 - getDisabledTools returns empty for unknown user`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId
            ) { userId ->
                val disabled = service.getDisabledTools(userId)
                assertTrue(disabled.isEmpty(), "Disabled tools should be empty for user '$userId', got: $disabled")
            }
        }
    }

    /**
     * Property 4 — getEffectivePermissions returns empty map
     * when user has no saved permissions record.
     */
    @Test
    fun `Property 4 - getEffectivePermissions returns empty for unknown user`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbUserId
            ) { userId ->
                val response = service.getEffectivePermissions(userId)
                assertTrue(
                    response.permissions.isEmpty(),
                    "Permissions should be empty for user '$userId', got: ${response.permissions}"
                )
            }
        }
    }
}
