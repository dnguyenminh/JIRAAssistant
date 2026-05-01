package com.assistant.server.agent.memory

import com.assistant.agent.memory.*
import com.assistant.config.JsonConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for StructuredMemory (Properties 3, 6, 7, 8, 9).
 */
@OptIn(ExperimentalKotest::class)
class StructuredMemoryPropertyTest {

    private val json = JsonConfig.instance
    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 3: StructuredMemory serialization round-trip.
     *
     * For any StructuredMemory with arbitrary schema and slot contents,
     * serializing to JSON then deserializing back produces equivalent data.
     *
     * **Validates: Requirements 2.4, 2.8**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-3")
    fun `serialization round-trip preserves schema and slot contents`() {
        runBlocking {
            checkAll(cfg, Arb.structuredMemory()) { original ->
                val serialized = json.encodeToString(
                    StructuredMemory.serializer(), original
                )
                val restored = json.decodeFromString(
                    StructuredMemory.serializer(), serialized
                )
                restored.schema shouldBe original.schema
                for (slot in original.schema) {
                    restored.getSlot(slot.name) shouldBe
                        original.getSlot(slot.name)
                }
            }
        }
    }

    /**
     * Property 6: StructuredMemory completeness calculation.
     *
     * For each slot, completeness equals currentSize/maxSize
     * clamped to [0.0, 1.0].
     *
     * **Validates: Requirements 2.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-6")
    fun `completeness values match expected formula clamped 0 to 1`() {
        runBlocking {
            checkAll(cfg, Arb.structuredMemory()) { memory ->
                val completeness = memory.getCompleteness()
                for (slot in memory.schema) {
                    val entries = memory.getSlot(slot.name)
                    val expected = expectedCompleteness(slot, entries)
                    val actual = completeness[slot.name]!!
                    actual.shouldBeBetween(0.0, 1.0, 0.0)
                    actual.shouldBeBetween(
                        expected - 0.0001, expected + 0.0001, 0.0
                    )
                }
            }
        }
    }

    /**
     * Property 7: StructuredMemory slot capacity enforcement.
     *
     * After storing maxSize entries, next store() returns SlotFullResult.
     *
     * **Validates: Requirements 2.5**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-7")
    fun `store returns SlotFullResult when slot is full`() {
        runBlocking {
            checkAll(cfg, Arb.slotSchema(), Arb.memoryEntry()) { schema, entry ->
                val safeSchema = schema.copy(
                    maxSize = schema.maxSize.coerceIn(1..20)
                )
                val memory = StructuredMemory(listOf(safeSchema))
                fillSlotToCapacity(memory, safeSchema, entry)
                val sizeBefore = computeCurrentSize(
                    safeSchema, memory.getSlot(safeSchema.name)
                )
                val overflow = memory.store(safeSchema.name, entry)
                overflow shouldBe SlotFullResult(
                    safeSchema.name, sizeBefore
                )
            }
        }
    }

    /**
     * Property 8: StructuredMemory clear resets all slots.
     *
     * After clear(), getSlot returns empty for every slot
     * and getTotalSize is 0.
     *
     * **Validates: Requirements 2.6**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-8")
    fun `clear resets all slots to empty`() {
        runBlocking {
            checkAll(cfg, Arb.structuredMemory()) { memory ->
                memory.clear()
                for (slot in memory.schema) {
                    memory.getSlot(slot.name) shouldBe emptyList()
                }
                memory.getTotalSize() shouldBe 0
            }
        }
    }

    /**
     * Property 9: StructuredMemory getTotalSize invariant.
     *
     * getTotalSize() equals sum of entry.data.length across all slots.
     *
     * **Validates: Requirements 2.7**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-9")
    fun `getTotalSize equals sum of all entry data lengths`() {
        runBlocking {
            checkAll(cfg, Arb.structuredMemory()) { memory ->
                val expected = memory.schema.sumOf { slot ->
                    memory.getSlot(slot.name).sumOf { it.data.length }
                }
                memory.getTotalSize() shouldBe expected
            }
        }
    }
}
