package com.assistant.server.document

import com.assistant.server.document.models.TicketGraph
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe holder for passing TicketGraph from DeepJiraContentExtractor
 * to AnalysisRoutes without changing AIOrchestratorImpl interface.
 *
 * Uses store/take pattern: DeepJiraContentExtractor stores the graph
 * after extraction, AnalysisRoutes takes (removes) it after analysis.
 * Requirements: 3.2
 */
class TicketGraphHolder {
    private val graphMap = ConcurrentHashMap<String, TicketGraph>()

    /** Store a graph for later retrieval. */
    fun store(ticketId: String, graph: TicketGraph) {
        graphMap[ticketId] = graph
    }

    /** Take (remove) the graph for a ticket. Returns null if not present. */
    fun take(ticketId: String): TicketGraph? {
        return graphMap.remove(ticketId)
    }
}
