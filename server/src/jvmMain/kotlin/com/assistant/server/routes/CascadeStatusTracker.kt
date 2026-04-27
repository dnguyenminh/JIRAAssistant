package com.assistant.server.routes

import com.assistant.ai.deepanalysis.models.CascadeResult
import com.assistant.ai.deepanalysis.models.CascadeStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for tracking cascade analysis progress per ticket.
 * Similar to AnalysisStatusTracker but for cascading analysis.
 *
 * Requirements: 26.8, 26.10
 */
object CascadeStatusTracker {
    private val results = ConcurrentHashMap<String, CascadeResult>()

    fun update(ticketId: String, result: CascadeResult) {
        results[ticketId] = result
    }

    fun get(ticketId: String): CascadeResult? = results[ticketId]

    fun remove(ticketId: String) { results.remove(ticketId) }

    fun isRunning(ticketId: String): Boolean {
        return results[ticketId]?.status == CascadeStatus.RUNNING
    }
}
