package com.assistant.agent.memory

import kotlinx.serialization.Serializable

/**
 * Generic typed memory container with configurable named slots.
 * Each slot has a schema (name, type, maxSize) and holds MemoryEntry items.
 *
 * Capacity enforcement:
 * - STRING slots: maxSize = max total characters (sum of entry.data.length)
 * - LIST/MAP slots: maxSize = max number of entries
 */
@Serializable(with = StructuredMemorySerializer::class)
class StructuredMemory(
    val schema: List<SlotSchema>
) {
    private val slots: MutableMap<String, MutableList<MemoryEntry>> =
        mutableMapOf()

    /**
     * Store an entry in the named slot.
     * @return null on success, SlotFullResult if the slot is full
     */
    fun store(slotName: String, entry: MemoryEntry): SlotFullResult? {
        val slotSchema = schema.find { it.name == slotName }
            ?: return null
        val entries = slots.getOrPut(slotName) { mutableListOf() }
        if (isSlotFull(slotSchema, entries, entry)) {
            return SlotFullResult(slotName, currentSize(slotSchema, entries))
        }
        entries.add(entry)
        return null
    }

    /** Get all entries for a named slot. */
    fun getSlot(slotName: String): List<MemoryEntry> =
        slots[slotName]?.toList() ?: emptyList()

    /**
     * Completeness map: slot name → fill ratio clamped to [0.0, 1.0].
     * STRING: sum(data.length) / maxSize. LIST/MAP: entries.size / maxSize.
     */
    fun getCompleteness(): Map<String, Double> =
        schema.associate { it.name to computeCompleteness(it) }

    /** Sum of entry.data.length across ALL entries in ALL slots. */
    fun getTotalSize(): Int =
        slots.values.sumOf { entries ->
            entries.sumOf { it.data.length }
        }

    /** Reset all slots to empty. */
    fun clear() = slots.clear()

    private fun isSlotFull(
        schema: SlotSchema,
        entries: List<MemoryEntry>,
        newEntry: MemoryEntry
    ): Boolean = when (schema.type) {
        SlotType.STRING -> {
            val current = entries.sumOf { it.data.length }
            current + newEntry.data.length > schema.maxSize
        }
        SlotType.LIST, SlotType.MAP ->
            entries.size >= schema.maxSize
    }

    private fun currentSize(
        schema: SlotSchema,
        entries: List<MemoryEntry>
    ): Int = when (schema.type) {
        SlotType.STRING -> entries.sumOf { it.data.length }
        SlotType.LIST, SlotType.MAP -> entries.size
    }

    private fun computeCompleteness(slotSchema: SlotSchema): Double {
        val entries = slots[slotSchema.name] ?: emptyList()
        if (slotSchema.maxSize <= 0) return 0.0
        val ratio = currentSize(slotSchema, entries).toDouble() /
            slotSchema.maxSize
        return ratio.coerceIn(0.0, 1.0)
    }

    companion object {
        /** Reconstruct from deserialized surrogate data. */
        fun fromSurrogate(
            schema: List<SlotSchema>,
            slots: Map<String, List<MemoryEntry>>
        ): StructuredMemory {
            val memory = StructuredMemory(schema)
            slots.forEach { (name, entries) ->
                memory.slots[name] = entries.toMutableList()
            }
            return memory
        }
    }
}
