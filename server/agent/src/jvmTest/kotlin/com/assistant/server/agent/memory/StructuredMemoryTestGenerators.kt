package com.assistant.server.agent.memory

import com.assistant.agent.memory.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

// ── Inline Arb generators for StructuredMemory tests ───────────────

fun Arb.Companion.slotType(): Arb<SlotType> =
    Arb.enum<SlotType>()

fun Arb.Companion.slotSchema(): Arb<SlotSchema> = arbitrary {
    SlotSchema(
        name = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
        type = Arb.slotType().bind(),
        maxSize = Arb.int(1..100).bind()
    )
}

fun Arb.Companion.memoryEntry(): Arb<MemoryEntry> = arbitrary {
    MemoryEntry(
        data = Arb.string(1..50, Codepoint.alphanumeric()).bind(),
        source = Arb.string(0..20, Codepoint.alphanumeric()).bind(),
        toolName = Arb.string(0..20, Codepoint.alphanumeric()).bind(),
        timestamp = Arb.string(0..20, Codepoint.alphanumeric()).bind()
    )
}

/** Generate a StructuredMemory with unique slot names and random entries. */
fun Arb.Companion.structuredMemory(): Arb<StructuredMemory> =
    arbitrary {
        val schemaSize = Arb.int(1..5).bind()
        val schemas = buildUniqueSlotSchemas(schemaSize)
        val memory = StructuredMemory(schemas)
        populateMemory(memory, schemas)
        memory
    }

private suspend fun ArbitraryBuilderContext.buildUniqueSlotSchemas(
    count: Int
): List<SlotSchema> {
    val names = mutableSetOf<String>()
    val schemas = mutableListOf<SlotSchema>()
    repeat(count) {
        var schema = Arb.slotSchema().bind()
        while (schema.name in names) {
            schema = Arb.slotSchema().bind()
        }
        names.add(schema.name)
        schemas.add(schema)
    }
    return schemas
}

private suspend fun ArbitraryBuilderContext.populateMemory(
    memory: StructuredMemory,
    schemas: List<SlotSchema>
) {
    for (slot in schemas) {
        val entryCount = Arb.int(0..3).bind()
        repeat(entryCount) {
            val entry = Arb.memoryEntry().bind()
            memory.store(slot.name, entry)
        }
    }
}

// ── Test helpers ────────────────────────────────────────────────────

fun expectedCompleteness(
    slot: SlotSchema,
    entries: List<MemoryEntry>
): Double {
    if (slot.maxSize <= 0) return 0.0
    val size = when (slot.type) {
        SlotType.STRING -> entries.sumOf { it.data.length }
        SlotType.LIST, SlotType.MAP -> entries.size
    }
    return (size.toDouble() / slot.maxSize).coerceIn(0.0, 1.0)
}

fun fillSlotToCapacity(
    memory: StructuredMemory,
    schema: SlotSchema,
    entry: MemoryEntry
) {
    when (schema.type) {
        SlotType.STRING -> fillStringSlot(memory, schema, entry)
        SlotType.LIST, SlotType.MAP ->
            repeat(schema.maxSize) { memory.store(schema.name, entry) }
    }
}

private fun fillStringSlot(
    memory: StructuredMemory,
    schema: SlotSchema,
    entry: MemoryEntry
) {
    if (entry.data.isEmpty()) return
    val fits = schema.maxSize / entry.data.length
    repeat(fits) { memory.store(schema.name, entry) }
}


/** Compute the current size of a slot based on its type. */
fun computeCurrentSize(
    schema: SlotSchema,
    entries: List<MemoryEntry>
): Int = when (schema.type) {
    SlotType.STRING -> entries.sumOf { it.data.length }
    SlotType.LIST, SlotType.MAP -> entries.size
}
