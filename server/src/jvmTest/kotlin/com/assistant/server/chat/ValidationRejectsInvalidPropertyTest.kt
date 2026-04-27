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
 * Property 6: Validation rejects invalid entries
 *
 * For any permissions map with key not matching format or value
 * not being "enabled"/"disabled", validate SHALL fail.
 *
 * **Validates: Requirements 3.7**
 *
 * Feature: per-user-tool-permissions, Property 6
 */
@OptIn(ExperimentalKotest::class)
class ValidationRejectsInvalidPropertyTest {

    private lateinit var service: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        service = UserToolPermissionService(
            permRepo = InMemoryPermissionRepo(),
            mcpServerRepo = InMemoryMcpServerRepo()
        )
    }

    private val arbServerId: Arb<String> =
        Arb.string(2..12, Codepoint.az())

    private val arbToolName: Arb<String> =
        Arb.string(2..15, Codepoint.az())

    private val arbValue: Arb<String> =
        Arb.element("enabled", "disabled")

    private val arbInvalidValue: Arb<String> =
        Arb.string(3..20, Codepoint.az())
            .filter { it !in setOf("enabled", "disabled") }

    private val cfg = PropTestConfig(iterations = 15)

    /**
     * Invalid key format — keys without "::" separator.
     */
    @Test
    fun `Property 6 - invalid key missing separator`() {
        runBlocking {
            checkAll(cfg, arbServerId, arbValue) { key, value ->
                val perms = mapOf(key to value)
                val result = service.validate(perms)
                assertTrue(
                    result.isFailure,
                    "Expected failure for key without '::': '$key'"
                )
            }
        }
    }

    /**
     * Invalid key format — empty serverId or toolName.
     */
    @Test
    fun `Property 6 - invalid key empty parts`() {
        val emptyPartKeys = Arb.element(
            "::tool", "server::", "::"
        )
        runBlocking {
            checkAll(cfg, emptyPartKeys, arbValue) { key, value ->
                val perms = mapOf(key to value)
                val result = service.validate(perms)
                assertTrue(
                    result.isFailure,
                    "Expected failure for key with empty part: '$key'"
                )
            }
        }
    }

    /**
     * Invalid value — valid key but value not "enabled"/"disabled".
     */
    @Test
    fun `Property 6 - invalid value rejected`() {
        runBlocking {
            checkAll(
                cfg, arbServerId, arbToolName, arbInvalidValue
            ) { sid, tool, badVal ->
                val key = "$sid::$tool"
                val perms = mapOf(key to badVal)
                val result = service.validate(perms)
                assertTrue(
                    result.isFailure,
                    "Expected failure for invalid value '$badVal'"
                )
            }
        }
    }

    /**
     * Valid entries pass validation.
     */
    @Test
    fun `Property 6 - valid entries pass validation`() {
        val arbEntry = Arb.bind(
            arbServerId, arbToolName, arbValue
        ) { sid, tool, v -> "$sid::$tool" to v }
        val arbPerms = Arb.list(arbEntry, 0..8)
            .map { it.toMap() }
        runBlocking {
            checkAll(cfg, arbPerms) { perms ->
                val result = service.validate(perms)
                assertTrue(
                    result.isSuccess,
                    "Expected success for valid perms: $perms"
                )
            }
        }
    }

    /**
     * savePermissions also rejects invalid entries.
     */
    @Test
    fun `Property 6 - savePermissions rejects invalid entries`() {
        runBlocking {
            checkAll(
                cfg, arbServerId, arbToolName, arbInvalidValue
            ) { sid, tool, badVal ->
                val perms = mapOf("$sid::$tool" to badVal)
                val result = service.savePermissions("u1", perms)
                assertTrue(
                    result.isFailure,
                    "savePermissions should reject invalid value"
                )
            }
        }
    }
}
