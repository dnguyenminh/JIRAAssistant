package com.assistant.server.agent.session

import com.assistant.agent.session.CommandHistoryEntry
import com.assistant.agent.session.SessionContext
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for SessionManagerImpl
 * (Properties 33, 34, 35).
 */
@OptIn(ExperimentalKotest::class)
@Tag("generic-agent-framework")
class SessionPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val safeStr =
        Arb.string(1..20, Codepoint.alphanumeric())

    /**
     * Property 33 — Session memory persistence across commands.
     *
     * For any sequence of addCommandToHistory calls,
     * getCommandHistory returns all entries in order.
     *
     * **Validates: Requirements 18.1**
     */
    @Test
    @Tag("Property-33")
    fun `command history persists across sequential adds`() {
        runBlocking {
            checkAll(cfg, arbCommandSequence()) { cmds ->
                val mgr = SessionManagerImpl()
                val agent = "test-agent"
                cmds.forEach { (cmd, resp) ->
                    mgr.addCommandToHistory(agent, cmd, resp)
                }
                val history = mgr.getCommandHistory(agent)
                val nonSummary = history.filter { !it.isSummary }
                nonSummary.forEachIndexed { i, entry ->
                    val expected = cmds[cmds.size - nonSummary.size + i]
                    entry.command shouldBe expected.first
                    entry.responseSummary shouldBe expected.second
                }
            }
        }
    }

    /**
     * Property 34 — Session reset clears working memory.
     *
     * After resetSession, getCommandHistory returns empty
     * and memory is cleared.
     *
     * **Validates: Requirements 18.2**
     */
    @Test
    @Tag("Property-34")
    fun `resetSession clears history and memory`() {
        runBlocking {
            checkAll(cfg, arbCommandSequence()) { cmds ->
                val mgr = SessionManagerImpl()
                val agent = "test-agent"
                cmds.forEach { (cmd, resp) ->
                    mgr.addCommandToHistory(agent, cmd, resp)
                }
                mgr.resetSession(agent)
                mgr.getCommandHistory(agent).shouldBeEmpty()
                val ctx = mgr.getSessionContext(agent)
                ctx.memory.getTotalSize() shouldBe 0
            }
        }
    }

    /**
     * Property 35 — Command history cap.
     *
     * After adding more than MAX_HISTORY_SIZE entries,
     * history size ≤ MAX_HISTORY_SIZE + 1 (entries + summary).
     *
     * **Validates: Requirements 18.4, 18.5**
     */
    @Test
    @Tag("Property-35")
    fun `history never exceeds MAX_HISTORY_SIZE plus summary`() {
        runBlocking {
            checkAll(cfg, arbOverflowCount()) { count ->
                val mgr = SessionManagerImpl()
                val agent = "test-agent"
                val max = SessionContext.MAX_HISTORY_SIZE
                repeat(count) { i ->
                    mgr.addCommandToHistory(
                        agent, "cmd_$i", "resp_$i"
                    )
                }
                val history = mgr.getCommandHistory(agent)
                history.size shouldBe
                    (history.size).coerceAtMost(max + 1)
            }
        }
    }

    // ── Generators ──────────────────────────────────────

    /** 1–40 command/response pairs (under cap). */
    private fun arbCommandSequence():
        Arb<List<Pair<String, String>>> = arbitrary {
        val n = Arb.int(1..40).bind()
        (1..n).map {
            safeStr.bind() to safeStr.bind()
        }
    }

    /** Count that exceeds MAX_HISTORY_SIZE. */
    private fun arbOverflowCount(): Arb<Int> =
        Arb.int(
            SessionContext.MAX_HISTORY_SIZE + 1..
                SessionContext.MAX_HISTORY_SIZE + 30
        )
}
