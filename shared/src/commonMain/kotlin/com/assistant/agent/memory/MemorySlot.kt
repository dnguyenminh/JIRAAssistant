package com.assistant.agent.memory

import kotlinx.serialization.Serializable

/**
 * Type of a memory slot, determining how capacity is measured.
 * - STRING: maxSize = max total characters (sum of entry.data.length)
 * - LIST: maxSize = max number of entries
 * - MAP: maxSize = max number of entries
 */
@Serializable
enum class SlotType { STRING, LIST, MAP }

/**
 * Schema definition for a single memory slot.
 * @param name Unique slot identifier
 * @param type Determines capacity measurement strategy
 * @param maxSize Maximum capacity (chars for STRING, entries for LIST/MAP)
 */
@Serializable
data class SlotSchema(
    val name: String,
    val type: SlotType,
    val maxSize: Int
)

/**
 * A single entry stored in a memory slot with provenance metadata.
 */
@Serializable
data class MemoryEntry(
    val data: String,
    val source: String = "",
    val toolName: String = "",
    val timestamp: String = ""
)

/**
 * Returned when a store() call is rejected because the slot is full.
 */
@Serializable
data class SlotFullResult(
    val slotName: String,
    val currentSize: Int
)
