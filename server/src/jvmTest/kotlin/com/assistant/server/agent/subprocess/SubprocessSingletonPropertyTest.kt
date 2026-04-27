package com.assistant.server.agent.subprocess

import com.assistant.agent.subprocess.SubprocessConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * Property 24 — Subprocess singleton reuse.
 *
 * For any sequence of N commands (N ≥ 2) sent to the same
 * agent type, the SubprocessManager SHALL reuse the same
 * subprocess — the total number of process spawns for that
 * agent type SHALL be exactly 1 (assuming no crashes).
 *
 * Tests the singleton reuse logic by verifying that
 * [SubprocessManagerImpl.getOrSpawnSubprocess] returns the
 * same [ManagedSubprocess] instance for repeated calls.
 *
 * **Validates: Requirements 13.1**
 */
@OptIn(ExperimentalKotest::class)
@Tag("generic-agent-framework")
class SubprocessSingletonPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val safeStr =
        Arb.string(1..15, Codepoint.alphanumeric())

    /**
     * Property 24: Subprocess singleton reuse.
     *
     * Pre-populates the subprocesses map with a fake
     * [ManagedSubprocess] and verifies that N calls to
     * [getOrSpawnSubprocess] return the same instance.
     *
     * **Validates: Requirements 13.1**
     */
    @Test
    @Tag("Property-24")
    fun `same agent type reuses singleton subprocess`() {
        runBlocking {
            checkAll(cfg, safeStr, Arb.int(2..8)) { agentType, n ->
                val config = SubprocessConfig(
                    agentType = agentType,
                    cliCommand = "echo"
                )
                val manager = SubprocessManagerImpl(
                    configs = mapOf(agentType to config)
                )
                val fake = fakeManagedSubprocess(agentType)
                manager.subprocesses[agentType] = fake

                val instances = (1..n).map {
                    manager.getOrSpawnSubprocess(agentType)
                }

                instances.forEach { it shouldBe fake }
                manager.subprocesses.size shouldBe 1
            }
        }
    }

    /**
     * Property 24 (extended): different agent types get
     * different subprocess instances.
     *
     * **Validates: Requirements 13.1**
     */
    @Test
    @Tag("Property-24")
    fun `different agent types get separate subprocesses`() {
        runBlocking {
            checkAll(cfg, arbDistinctPair()) { (a, b) ->
                val configs = mapOf(
                    a to SubprocessConfig(a, "echo"),
                    b to SubprocessConfig(b, "echo")
                )
                val manager = SubprocessManagerImpl(configs)
                val fakeA = fakeManagedSubprocess(a)
                val fakeB = fakeManagedSubprocess(b)
                manager.subprocesses[a] = fakeA
                manager.subprocesses[b] = fakeB

                val gotA = manager.getOrSpawnSubprocess(a)
                val gotB = manager.getOrSpawnSubprocess(b)

                gotA shouldBe fakeA
                gotB shouldBe fakeB
                manager.subprocesses.size shouldBe 2
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────

    private fun fakeManagedSubprocess(
        agentType: String
    ): ManagedSubprocess {
        val process = FakeAliveProcess()
        return ManagedSubprocess(
            agentType = agentType,
            process = process,
            stdin = BufferedWriter(StringWriter()),
            stdout = BufferedReader(StringReader("")),
            stderr = BufferedReader(StringReader(""))
        )
    }

    private fun arbDistinctPair(): Arb<Pair<String, String>> =
        arbitrary {
            val a = safeStr.bind()
            var b = safeStr.bind()
            while (b == a) b = safeStr.bind()
            a to b
        }
}
