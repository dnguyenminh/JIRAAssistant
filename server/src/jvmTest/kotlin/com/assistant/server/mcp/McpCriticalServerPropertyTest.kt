package com.assistant.server.mcp

import com.assistant.mcp.models.McpServerHealth
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for critical server identification logic.
 *
 * The pure functions under test are replicated from ReadinessDialog.kt
 * (frontend Kotlin/JS) so they can be verified in JVM tests.
 *
 * **Validates: Requirements 5.2, 5.3**
 *
 * Feature: mcp-readiness-check, Property 6: Critical server identification and warning
 */
@OptIn(ExperimentalKotest::class)
class McpCriticalServerPropertyTest {

    // ── Replicated pure logic from ReadinessDialog.kt ───────────

    companion object {
        val CRITICAL_SERVERS: Map<String, Set<String>> = mapOf(
            "BRD" to setOf("knowledge_base", "database"),
            "FSD" to setOf("knowledge_base", "database"),
            "REQUIREMENT_SLIDES" to setOf("knowledge_base")
        )

        fun isCriticalForDocType(role: String, docType: String): Boolean =
            CRITICAL_SERVERS[docType]?.contains(role) == true

        fun areAllCriticalDown(
            servers: List<McpServerHealth>, docType: String
        ): Boolean {
            val roles = CRITICAL_SERVERS[docType] ?: return false
            return roles.isNotEmpty() && roles.all { role ->
                servers.any { it.role == role && !it.ready }
            }
        }
    }

    // ── Generators ──────────────────────────────────────────────

    private val knownDocTypes = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
    private val arbKnownDocType: Arb<String> = Arb.element(knownDocTypes)

    private val allRoles = listOf(
        "knowledge_base", "database", "markitdown", "jira_internal", "other"
    )
    private val arbRole: Arb<String> = Arb.element(allRoles)

    private val arbServerHealth: Arb<McpServerHealth> = Arb.bind(
        Arb.string(3..10, Codepoint.az()),
        Arb.string(3..10, Codepoint.az()),
        Arb.boolean(),
        Arb.int(0..10),
        arbRole
    ) { cid, sn, ready, tc, role ->
        McpServerHealth(
            configId = cid, serverName = sn, ready = ready,
            toolCount = tc,
            error = if (!ready) "some error" else null,
            role = role
        )
    }

    private val arbServerList: Arb<List<McpServerHealth>> =
        Arb.list(arbServerHealth, 0..8)

    private val arbUnknownDocType: Arb<String> =
        Arb.string(3..10, Codepoint.az())
            .filter { it.uppercase() !in knownDocTypes }

    // ── Property 6a: isCriticalForDocType correctness ───────────

    /**
     * For any known doc type and role, isCriticalForDocType returns
     * true iff the role is in CRITICAL_SERVERS[docType].
     *
     * **Validates: Requirements 5.2**
     */
    @Test
    fun `Property 6 - critical roles match CRITICAL_SERVERS mapping`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbKnownDocType, arbRole
            ) { docType, role ->
                val expected = CRITICAL_SERVERS[docType]!!.contains(role)
                assertEquals(
                    expected, isCriticalForDocType(role, docType),
                    "role='$role' docType='$docType'"
                )
            }
        }
    }

    // ── Property 6b: unknown docType → no critical servers ──────

    /**
     * For any unknown doc type, isCriticalForDocType always returns
     * false and areAllCriticalDown always returns false.
     *
     * **Validates: Requirements 5.2**
     */
    @Test
    fun `Property 6 - unknown docType has no critical servers`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbUnknownDocType, arbRole
            ) { docType, role ->
                assertFalse(
                    isCriticalForDocType(role, docType),
                    "Unknown docType '$docType' → never critical"
                )
            }
        }
    }

    @Test
    fun `Property 6 - unknown docType never triggers strong warning`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbUnknownDocType, arbServerList
            ) { docType, servers ->
                assertFalse(
                    areAllCriticalDown(servers, docType),
                    "Unknown docType '$docType' → no strong warning"
                )
            }
        }
    }

    // ── Property 6c: strong warning iff ALL critical down ───────

    /**
     * For any known doc type and server list, areAllCriticalDown is
     * true iff every critical role for that docType has at least one
     * server with that role AND ready == false.
     *
     * **Validates: Requirements 5.2, 5.3**
     */
    @Test
    fun `Property 6 - strong warning iff all critical servers unavailable`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbKnownDocType, arbServerList
            ) { docType, servers ->
                val criticalRoles = CRITICAL_SERVERS[docType]!!
                val expected = criticalRoles.all { role ->
                    servers.any { it.role == role && !it.ready }
                }
                assertEquals(
                    expected, areAllCriticalDown(servers, docType),
                    "docType='$docType' servers=$servers"
                )
            }
        }
    }

    // ── Property 6d: critical role missing from down list → no warning

    /**
     * For any known doc type, if at least one critical role has NO
     * down server entry at all, areAllCriticalDown must return false.
     *
     * The function checks `servers.any { it.role == role && !it.ready }`
     * for each critical role, so the warning fires only when every
     * critical role has at least one down server present.
     *
     * **Validates: Requirements 5.3**
     */
    @Test
    fun `Property 6 - missing critical role in down servers means no strong warning`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbKnownDocType, arbServerList
            ) { docType, baseServers ->
                val criticalRoles = CRITICAL_SERVERS[docType]!!
                val sparedRole = criticalRoles.first()
                // Remove all down servers for the spared role
                val filtered = baseServers.filter { s ->
                    !(s.role == sparedRole && !s.ready)
                }

                assertFalse(
                    areAllCriticalDown(filtered, docType),
                    "No down server for role '$sparedRole' → no strong warning"
                )
            }
        }
    }

    // ── Property 6e: all critical forced down → warning fires ───

    /**
     * For any known doc type, if we ensure every critical role has
     * a down server, areAllCriticalDown must return true.
     *
     * **Validates: Requirements 5.3**
     */
    @Test
    fun `Property 6 - all critical forced down triggers strong warning`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbKnownDocType, arbServerList
            ) { docType, baseServers ->
                val criticalRoles = CRITICAL_SERVERS[docType]!!
                // Inject a down server for every critical role
                val downServers = criticalRoles.map { role ->
                    McpServerHealth(
                        configId = "down-$role",
                        serverName = "down-$role",
                        ready = false, toolCount = 0,
                        error = "forced down", role = role
                    )
                }
                // Remove any ready servers with critical roles
                val filtered = baseServers.filter { s ->
                    !(s.role in criticalRoles && s.ready)
                }
                val servers = filtered + downServers

                assertTrue(
                    areAllCriticalDown(servers, docType),
                    "All critical down → strong warning"
                )
            }
        }
    }
}
