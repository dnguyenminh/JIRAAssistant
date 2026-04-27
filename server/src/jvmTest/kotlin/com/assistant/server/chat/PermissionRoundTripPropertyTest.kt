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
 * Property 3: Permissions round-trip
 *
 * For any valid permissions map, saving via PUT then reading via GET
 * SHALL return the same map.
 *
 * **Validates: Requirements 3.2, 3.3**
 *
 * Feature: per-user-tool-permissions, Property 3
 */
@OptIn(ExperimentalKotest::class)
class PermissionRoundTripPropertyTest {

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

    /** Arb for serverId segment: 2-12 lowercase alpha chars. */
    private val arbServerId: Arb<String> =
        Arb.string(2..12, Codepoint.az())

    /** Arb for toolName segment: 2-15 lowercase alpha chars. */
    private val arbToolName: Arb<String> =
        Arb.string(2..15, Codepoint.az())

    /** Arb for permission value: "enabled" or "disabled". */
    private val arbValue: Arb<String> =
        Arb.element("enabled", "disabled")

    /** Arb for a single valid permission entry. */
    private val arbEntry: Arb<Pair<String, String>> =
        Arb.bind(arbServerId, arbToolName, arbValue) { sid, tool, v ->
            "$sid::$tool" to v
        }

    /** Arb for a valid permissions map (0-8 entries). */
    private val arbPermissions: Arb<Map<String, String>> =
        Arb.list(arbEntry, 0..8).map { it.toMap() }

    /**
     * Property 3 — save then read returns identical permissions.
     */
    @Test
    fun `Property 3 - save then get returns same permissions`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15),
                arbPermissions
            ) { perms ->
                permRepo.clear()
                val userId = "test-user"

                service.savePermissions(userId, perms)
                val response = service.getEffectivePermissions(userId)

                assertEquals(
                    perms, response.permissions,
                    "Round-trip failed for $perms"
                )
            }
        }
    }

    /**
     * Property 3 — overwrite preserves only latest permissions.
     */
    @Test
    fun `Property 3 - overwrite replaces previous permissions`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 10),
                arbPermissions,
                arbPermissions
            ) { first, second ->
                permRepo.clear()
                val userId = "overwrite-user"

                service.savePermissions(userId, first)
                service.savePermissions(userId, second)
                val response = service.getEffectivePermissions(userId)

                assertEquals(
                    second, response.permissions,
                    "Overwrite failed: expected=$second"
                )
            }
        }
    }
}
