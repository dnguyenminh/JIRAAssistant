package com.assistant.document

import com.assistant.document.models.GenerationContext

/**
 * Collects data from multiple sources (main ticket, linked tickets,
 * sub-tasks, VectorStore attachment chunks) and aggregates them
 * into a unified GenerationContext for BRD/FSD generation.
 *
 * Server-side implementation: DocumentAggregatorImpl (Req 1.1–1.5).
 */
interface DocumentAggregator {
    /**
     * Aggregates ticket data into a GenerationContext.
     *
     * @param ticketId The main ticket ID to aggregate data for.
     * @return GenerationContext containing main ticket analysis,
     *         linked ticket analyses (max 20), attachment chunks (max 10),
     *         and sprint metadata.
     * @throws IllegalStateException if ticket has no deep analysis.
     */
    suspend fun aggregate(ticketId: String): GenerationContext
}
