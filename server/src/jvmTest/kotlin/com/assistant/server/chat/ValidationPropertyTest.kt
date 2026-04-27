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
 * not 'enabled'/'disabled', validate SHALL fail.
 *
 * **Validates: Requirements 3.7**
 *
 * Feature: per-user-tool-permissions, Property 6
 */
@OptIn(ExperimentalKotest::class)
class ValidationPropertyTest {

    private lateinit var service: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        service = UserToolPermissionService(
            permRepo = InMemoryPermissionRepo(),
            mcpServerRepo = InMemoryMcpServerRepo()
        )
    }

    private val arbSegment: Arb<String> =
        Arb.string(2..10, Codepoint.az())

    private val arbValue: Arb<String> =
        Arb.element("enabled", "disabled")

    /** Keys without "::" separator → invalid. */
    private val arbKeyNoSeparator: Arb<String> =
        Arb.string(3..20, Codepoint.az())

    /** Keys with empty left part → invalid. */
    private val arbKeyEmptyLeft: Arb<String> =
        arbSegment.map { "::$it" }

    /** Keys with empty right part → invalid. */
    private val arbKeyEmptyRight: Arb<String> =
        arbSegment.map { "$it::" }

    /** Values that are neither "enabled" nor "disabled". */
    private val arbBadValue: Arb<String> =
        Arb.string(1..12, Codepoint.az())
            .filter { it != "enabled" && it != "disabled" }

    @Test
    fun `Property 6 - key without separator is rejected`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbKeyNoSeparator) { key ->
                val result = service.validate(mapOf(key to "enabled"))
                assertTrue(result.isFailure, "Expected failure for key='$key'")
            }
        }
    }

    @Test
    fun `Property 6 - key with empty left part is rejected`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbKeyEmptyLeft) { key ->
                val result = service.validate(mapOf(key to "disabled"))
                assertTrue(result.isFailure, "Expected failure for key='$key'")
            }
        }
    }

    @Test
    fun `Property 6 - key with empty right part is rejected`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbKeyEmptyRight) { key ->
                val result = service.validate(mapOf(key to "enabled"))
                assertTrue(result.isFailure, "Expected failure for key='$key'")
            }
        }
    }

    @Test
    fun `Property 6 - invalid value is rejected`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbSegment, arbSegment, arbBadValue) { sid, tool, bad ->
                val key = "$sid::$tool"
                val result = service.validate(mapOf(key to bad))
                assertTrue(result.isFailure, "Expected failure for value='$bad'")
            }
        }
    }

    @Test
    fun `Property 6 - valid entries pass validation`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbSegment, arbSegment, arbValue) { sid, tool, v ->
                val key = "$sid::$tool"
                val result = service.validate(mapOf(key to v))
                assertTrue(result.isSuccess, "Expected success for $key=$v")
            }
        }
    }
}
