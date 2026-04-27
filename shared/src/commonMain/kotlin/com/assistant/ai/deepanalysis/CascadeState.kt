package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.CascadeLogEntry
import com.assistant.ai.deepanalysis.models.CascadeLogStatus
import com.assistant.ai.deepanalysis.models.CascadeResult
import com.assistant.ai.deepanalysis.models.CascadeStatus
import kotlinx.datetime.Clock

/**
 * Mutable state for a cascading analysis run.
 * Manages BFS queue, visited set, counters, and log entries.
 *
 * Requirements: 26.5 (BFS), 26.6 (visited set), 26.7 (safety limit)
 */
internal class CascadeState(val maxTickets: Int) {

    /** BFS queue of ticket keys to process. Req 26.5 */
    private val queue = ArrayDeque<String>()

    /** Visited ticket keys — prevents loops and duplicates. Req 26.6 */
    private val visited = mutableSetOf<String>()

    /** Accumulated log entries. */
    private val _logEntries = mutableListOf<CascadeLogEntry>()
    val logEntries: List<CascadeLogEntry> get() = _logEntries

    /** Progress counters. */
    var completedCount: Int = 0; private set
    var failedCount: Int = 0; private set
    var skippedCount: Int = 0; private set

    /** Enqueue a ticket if not already visited. Req 26.6 */
    fun enqueue(ticketId: String) {
        if (visited.add(ticketId)) {
            queue.addLast(ticketId)
        }
    }

    /** Check if a ticket key has not been visited yet. */
    fun isNew(ticketId: String): Boolean = ticketId !in visited

    /** Dequeue next ticket from BFS queue. */
    fun dequeue(): String = queue.removeFirst()

    /** Check if BFS queue has more tickets. */
    fun hasNext(): Boolean = queue.isNotEmpty()

    /** Req 26.7 — Check if total visited exceeds safety limit. */
    fun isLimitReached(): Boolean = visited.size > maxTickets

    fun markCompleted() { completedCount++ }
    fun markFailed() { failedCount++ }
    fun markSkipped() { skippedCount++ }

    /** Add a log entry with current timestamp. */
    fun log(status: CascadeLogStatus, ticketKey: String, message: String) {
        _logEntries.add(CascadeLogEntry(
            status = status,
            ticketKey = ticketKey,
            message = message,
            timestamp = Clock.System.now().toString()
        ))
    }

    /** Total tickets discovered (visited set size). */
    val totalDiscovered: Int get() = visited.size
}

/** Build final CascadeResult from state. */
internal fun buildResult(state: CascadeState): CascadeResult {
    val status = if (state.failedCount > 0 && state.completedCount == 0) {
        CascadeStatus.FAILED
    } else {
        CascadeStatus.COMPLETED
    }
    return CascadeResult(
        status = status,
        logEntries = state.logEntries,
        totalTickets = state.totalDiscovered,
        completedTickets = state.completedCount,
        failedTickets = state.failedCount
    )
}
