package com.assistant.agent.memory

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Surrogate for serializing StructuredMemory (schema + slot contents).
 */
@Serializable
private data class StructuredMemorySurrogate(
    val schema: List<SlotSchema>,
    val slots: Map<String, List<MemoryEntry>>
)

/**
 * Custom serializer for StructuredMemory.
 * Converts to/from a surrogate that captures both schema and slot data.
 */
object StructuredMemorySerializer : KSerializer<StructuredMemory> {

    private val surrogateSerializer = StructuredMemorySurrogate.serializer()

    override val descriptor: SerialDescriptor
        get() = surrogateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: StructuredMemory) {
        val slotsMap = value.schema.associate { slot ->
            slot.name to value.getSlot(slot.name)
        }
        val surrogate = StructuredMemorySurrogate(
            schema = value.schema,
            slots = slotsMap
        )
        encoder.encodeSerializableValue(surrogateSerializer, surrogate)
    }

    override fun deserialize(decoder: Decoder): StructuredMemory {
        val surrogate = decoder.decodeSerializableValue(surrogateSerializer)
        return StructuredMemory.fromSurrogate(
            schema = surrogate.schema,
            slots = surrogate.slots
        )
    }
}
