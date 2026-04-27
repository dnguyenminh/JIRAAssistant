package com.assistant.server.agent.subprocess

import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property 25 — Command_Mutex sequential execution.
 *
 * For any set of N concurrent commands submitted to the same
 * agent subprocess, the commands SHALL execute sequentially —
 * no command overlap occurs.
 *
 * Tests the mutex serialization by simulating concurrent
 * access to a shared [Mutex] (the same mechanism used by
 * [ManagedSubprocess.commandMutex]).
 *
 * **Validates: Requirements 13.3**
 */
@OptIn(ExperimentalKotest::class)
@Tag("generic-agent-framework")
class CommandMutexPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 25: Command_Mutex sequential execution.
     *
     * Launches N concurrent coroutines that each acquire the
     * same [Mutex], increment a counter, delay briefly, then
     * decrement. If the mutex serializes correctly, the
     * concurrent count never exceeds 1.
     *
     * **Validates: Requirements 13.3**
     */
    @Test
    @Tag("Property-25")
    fun `concurrent commands are serialized by mutex`() {
        runBlocking {
            checkAll(cfg, Arb.int(2..8)) { concurrency ->
                val mutex = Mutex()
                val concurrent = AtomicInteger(0)
                val maxConcurrent = AtomicInteger(0)
                val completionOrder = CopyOnWriteArrayList<Int>()

                val jobs = (0 until concurrency).map { i ->
                    async {
                        mutex.lock()
                        try {
                            val c = concurrent.incrementAndGet()
                            maxConcurrent.updateAndGet { max ->
                                maxOf(max, c)
                            }
                            delay(1)
                            completionOrder.add(i)
                            concurrent.decrementAndGet()
                        } finally {
                            mutex.unlock()
                        }
                    }
                }
                jobs.awaitAll()

                maxConcurrent.get() shouldBe 1
                completionOrder.size shouldBe concurrency
            }
        }
    }

    /**
     * Property 25 (extended): all commands complete even
     * under contention.
     *
     * **Validates: Requirements 13.3**
     */
    @Test
    @Tag("Property-25")
    fun `all queued commands eventually complete`() {
        runBlocking {
            checkAll(cfg, Arb.int(2..10)) { count ->
                val mutex = Mutex()
                val completed = AtomicInteger(0)

                val jobs = (0 until count).map {
                    async {
                        mutex.lock()
                        try {
                            completed.incrementAndGet()
                        } finally {
                            mutex.unlock()
                        }
                    }
                }
                jobs.awaitAll()

                completed.get() shouldBe count
            }
        }
    }
}
