package com.assistant.server.document.models

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Arbitrary generator for [TraversalConfig] with values that intentionally
 * span well outside the valid clamping ranges (negative, zero, very large).
 */
fun Arb.Companion.arbTraversalConfig(): Arb<TraversalConfig> = arbitrary {
    TraversalConfig(
        maxDepth = Arb.int(-50..100).bind(),
        maxTickets = Arb.int(-100..2000).bind(),
        maxCommentsPerTicket = Arb.int(-200..2000).bind(),
        cacheTtlMinutes = Arb.int(-100..3000).bind()
    )
}

/**
 * Property 12: TraversalConfig Validation — Clamping.
 *
 * For any integer values for maxDepth, maxTickets, maxCommentsPerTicket,
 * and cacheTtlMinutes, `TraversalConfig(...).validated()` SHALL return a
 * config with all four clamped fields within their valid ranges.
 *
 * **Validates: Requirements 7.1, 7.3, 3.7, 15.2**
 */
@OptIn(ExperimentalKotest::class)
class TraversalConfigPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * **Validates: Requirements 7.1, 7.3, 1.2**
     *
     * validated() always clamps maxDepth to 1..20 and maxTickets to 1..1000,
     * regardless of the original input values.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 12: Clamping")
    fun `validated clamps maxDepth and maxTickets into valid ranges`() {
        runBlocking {
            checkAll(cfg, Arb.arbTraversalConfig()) { raw ->
                val v = raw.validated()
                assertTrue(v.maxDepth in 1..20) {
                    "maxDepth ${v.maxDepth} out of 1..20 (input=${raw.maxDepth})"
                }
                assertTrue(v.maxTickets in 1..1000) {
                    "maxTickets ${v.maxTickets} out of 1..1000 (input=${raw.maxTickets})"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.7, 15.2**
     *
     * validated() always clamps maxCommentsPerTicket to 10..1000 and
     * cacheTtlMinutes to 5..1440, regardless of the original input values.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 12: Clamping")
    fun `validated clamps comments and cache TTL into valid ranges`() {
        runBlocking {
            checkAll(cfg, Arb.arbTraversalConfig()) { raw ->
                val v = raw.validated()
                assertTrue(v.maxCommentsPerTicket in 10..1000) {
                    "maxCommentsPerTicket ${v.maxCommentsPerTicket} out of 10..1000 (input=${raw.maxCommentsPerTicket})"
                }
                assertTrue(v.cacheTtlMinutes in 5..1440) {
                    "cacheTtlMinutes ${v.cacheTtlMinutes} out of 5..1440 (input=${raw.cacheTtlMinutes})"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 7.1, 7.3, 3.7, 15.2**
     *
     * validated() is idempotent — calling it twice yields the same result.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 12: Clamping")
    fun `validated is idempotent`() {
        runBlocking {
            checkAll(cfg, Arb.arbTraversalConfig()) { raw ->
                val once = raw.validated()
                val twice = once.validated()
                assertTrue(once == twice) {
                    "validated() not idempotent: first=$once, second=$twice"
                }
            }
        }
    }
}
