package com.assistant.server.document.curation

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.kb.KBRecord
import com.assistant.server.document.curation.models.TicketClassification

/**
 * Classifies linked tickets by temporal relationship to the root ticket.
 *
 * Requirements: 2.1
 */
interface TemporalClassifier {
    /**
     * Classify a linked ticket relative to the root ticket.
     *
     * @param rootTicket The primary ticket being analyzed
     * @param linkedTicket The linked ticket to classify
     * @param linkedKb Optional KB record for the linked ticket
     * @return Classification result with temporal and content classification
     */
    fun classify(
        rootTicket: StructuredTicketContent,
        linkedTicket: StructuredTicketContent,
        linkedKb: KBRecord?
    ): TicketClassification
}
