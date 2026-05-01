package com.assistant.server.document.models

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.SprintMetadata
import com.assistant.kb.KBRecord
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Surrogate data class used for serialization of [EnrichedContext].
 *
 * Flattens all fields (parent + child) into a single serializable structure,
 * avoiding duplicate serial name conflicts with [GenerationContext].
 */
@Serializable
private data class EnrichedContextSurrogate(
    val mainTicket: KBRecord,
    val linkedTicketAnalyses: List<KBRecord> = emptyList(),
    val attachmentChunks: List<AttachmentChunkInfo> = emptyList(),
    val sprintMetadata: SprintMetadata? = null,
    val allTickets: List<StructuredTicketContent> = emptyList(),
    val ticketRelationships: List<TicketEdge> = emptyList(),
    val rawComments: Map<String, List<FullComment>> = emptyMap(),
    val allAttachmentChunks: List<AttachmentChunkInfo> = emptyList(),
    val traversalMetadata: TraversalMetadata? = null,
    val ticketDepthMap: Map<String, Int> = emptyMap()
)

/**
 * Custom serializer for [EnrichedContext] using surrogate pattern.
 *
 * Delegates to [EnrichedContextSurrogate] to serialize/deserialize all fields
 * (both inherited and new) without kotlinx.serialization inheritance conflicts.
 */
internal object EnrichedContextSerializer : KSerializer<EnrichedContext> {

    override val descriptor: SerialDescriptor =
        EnrichedContextSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: EnrichedContext) {
        val surrogate = EnrichedContextSurrogate(
            mainTicket = value.mainTicket,
            linkedTicketAnalyses = value.linkedTicketAnalyses,
            attachmentChunks = value.attachmentChunks,
            sprintMetadata = value.sprintMetadata,
            allTickets = value.allTickets,
            ticketRelationships = value.ticketRelationships,
            rawComments = value.rawComments,
            allAttachmentChunks = value.allAttachmentChunks,
            traversalMetadata = value.traversalMetadata,
            ticketDepthMap = value.ticketDepthMap
        )
        encoder.encodeSerializableValue(
            EnrichedContextSurrogate.serializer(), surrogate
        )
    }

    override fun deserialize(decoder: Decoder): EnrichedContext {
        val surrogate = decoder.decodeSerializableValue(
            EnrichedContextSurrogate.serializer()
        )
        return EnrichedContext(
            mainTicket = surrogate.mainTicket,
            linkedTicketAnalyses = surrogate.linkedTicketAnalyses,
            attachmentChunks = surrogate.attachmentChunks,
            sprintMetadata = surrogate.sprintMetadata,
            allTickets = surrogate.allTickets,
            ticketRelationships = surrogate.ticketRelationships,
            rawComments = surrogate.rawComments,
            allAttachmentChunks = surrogate.allAttachmentChunks,
            traversalMetadata = surrogate.traversalMetadata,
            ticketDepthMap = surrogate.ticketDepthMap
        )
    }
}
