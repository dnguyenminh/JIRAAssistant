package com.assistant.server.jobs

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property 5: Throttle permits DB write only when both conditions met.
 *
 * For any sequence of (progress, timestamp) pairs, a DB write SHALL
 * occur only when BOTH: (a) progress changed ≥1% since last write,
 * AND (b) ≥2000ms elapsed since last write. The sole exception is
 * the final write at 85% which always bypasses throttle.
 *
 * **Validates: Requirements 2.3, 5.1, 5.2**
 *
 * Feature: streaming-generation-progress,
 * Property 5: Throttle permits DB write only when both conditions met
 */
@OptIn(ExperimentalKotest::class)
class ThrottleLogicPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    /**
     * 5a — When progress delta < 1, throttle blocks the write
     * (regardless of time), unless newProgress == 85.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 5: Throttle permits DB write only when both conditions met")
    fun `Property 5a - zero progress delta blocks write`() = runTest {
        checkAll(cfg, Arb.int(35..84), Arb.long(2000L..60_000L)) { base, elapsed ->
            val result = shouldWriteProgress(base, base, elapsed, 0L)
            assertFalse(
                result,
                "Same progress $base with ${elapsed}ms elapsed should be blocked"
            )
        }
    }

    /**
     * 5b — When time delta < 2000ms, throttle blocks the write
     * (regardless of progress), unless newProgress == 85.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 5: Throttle permits DB write only when both conditions met")
    fun `Property 5b - insufficient time blocks write`() = runTest {
        checkAll(cfg, Arb.int(36..84), Arb.long(0L..1999L)) { newProg, elapsed ->
            val lastProg = newProg - 2 // ensure ≥1% delta
            val result = shouldWriteProgress(newProg, lastProg, elapsed, 0L)
            assertFalse(
                result,
                "progress=$newProg, last=$lastProg, elapsed=${elapsed}ms " +
                    "should be blocked (time < 2000ms)"
            )
        }
    }

    /**
     * 5c — When BOTH conditions met (≥1% delta AND ≥2000ms),
     * throttle permits the write.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 5: Throttle permits DB write only when both conditions met")
    fun `Property 5c - both conditions met permits write`() = runTest {
        checkAll(
            cfg,
            Arb.int(36..84),
            Arb.int(1..10),
            Arb.long(2000L..30_000L)
        ) { newProg, delta, elapsed ->
            val lastProg = (newProg - delta).coerceAtLeast(35)
            if (newProg - lastProg >= 1) {
                val result = shouldWriteProgress(newProg, lastProg, elapsed, 0L)
                assertTrue(
                    result,
                    "progress=$newProg, last=$lastProg, elapsed=${elapsed}ms " +
                        "should be permitted"
                )
            }
        }
    }

    /**
     * 5d — Final write at 85% always bypasses throttle,
     * even with zero progress delta and zero time elapsed.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 5: Throttle permits DB write only when both conditions met")
    fun `Property 5d - final 85 percent always bypasses throttle`() = runTest {
        checkAll(
            cfg,
            Arb.int(35..85),
            Arb.long(0L..60_000L)
        ) { lastProg, elapsed ->
            val result = shouldWriteProgress(85, lastProg, elapsed, 0L)
            assertTrue(
                result,
                "85% final write should always pass, " +
                    "lastProg=$lastProg, elapsed=${elapsed}ms"
            )
        }
    }

    /**
     * 5e — Simulates a realistic streaming sequence: random
     * (progress, timestamp) pairs fed through throttle, verifying
     * every permitted write satisfies both conditions or is 85%.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 5: Throttle permits DB write only when both conditions met")
    fun `Property 5e - sequence simulation respects throttle`() = runTest {
        checkAll(
            cfg,
            Arb.int(0..5),
            Arb.long(0L..5000L)
        ) { progressDelta, timeDelta ->
            var lastWritten = 35
            var lastTime = 0L
            var current = (lastWritten + progressDelta).coerceAtMost(84)
            val now = lastTime + timeDelta

            val allowed = shouldWriteProgress(current, lastWritten, now, lastTime)
            val bothMet = (current - lastWritten >= 1) && (now - lastTime >= 2000L)

            if (current != 85) {
                assertTrue(
                    allowed == bothMet,
                    "progress=$current, last=$lastWritten, " +
                        "elapsed=${timeDelta}ms: allowed=$allowed but bothMet=$bothMet"
                )
            }
        }
    }
}
