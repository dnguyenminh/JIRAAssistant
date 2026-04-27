package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.CascadeLogStatus
import kotlinx.coroutines.sync.withPermit

/**
 * Extension functions for CascadingAnalysisEngineImpl — ticket processing logic.
 * Separated to keep each file under 200 lines and functions under 20 lines.
 *
 * Requirements: 26.1-26.7
 */

/** Process a single ticket: check KB → analyze → discover related. */
internal suspend fun CascadingAnalysisEngineImpl.processOneTicket(
    ticketId: String,
    state: CascadeState
) {
    if (isAlreadyInKB(ticketId, state)) return
    analyzeAndDiscover(ticketId, state)
}

/** Req 26.2 — Check if ticket already exists in KB. */
private suspend fun CascadingAnalysisEngineImpl.isAlreadyInKB(
    ticketId: String,
    state: CascadeState
): Boolean {
    val existing = kbRepository.findByTicketId(ticketId)
    if (existing != null) {
        state.log(CascadeLogStatus.SKIPPED, ticketId, "Already in KB")
        state.markSkipped()
        return true
    }
    return false
}

/** Analyze ticket with AI semaphore, then discover related. Req 26.3, 26.4 */
private suspend fun CascadingAnalysisEngineImpl.analyzeAndDiscover(
    ticketId: String,
    state: CascadeState
) {
    state.log(CascadeLogStatus.ANALYZING, ticketId, "Analyzing...")
    try {
        aiSemaphore.withPermit {
            aiOrchestrator.analyzeTicket(ticketId)
        }
        state.markCompleted()
        state.log(CascadeLogStatus.COMPLETED, ticketId, "Analysis complete")
        discoverRelated(ticketId, state)
    } catch (e: Exception) {
        state.markFailed()
        state.log(CascadeLogStatus.FAILED, ticketId, "Failed: ${e.message ?: "Unknown"}")
    }
}

/** Req 26.1 — Discover related tickets and enqueue new ones. */
private suspend fun CascadingAnalysisEngineImpl.discoverRelated(
    ticketId: String,
    state: CascadeState
) {
    val content = try {
        jiraContentExtractor.extract(ticketId)
    } catch (_: Exception) {
        return // Cannot extract content — skip discovery
    }
    val related = RelatedTicketCollector.collect(ticketId, content)
    val newTickets = related.filter { state.isNew(it) }
    newTickets.forEach { key ->
        state.enqueue(key)
        state.log(CascadeLogStatus.DISCOVERED, key, "Discovered from $ticketId")
    }
}

/** Req 26.7 — Log warning when safety limit reached. */
internal fun CascadingAnalysisEngineImpl.logLimitReached(state: CascadeState) {
    val msg = "Safety limit reached (${state.maxTickets} tickets). Stopping cascade."
    println("[CascadingAnalysis] WARNING: $msg")
    state.log(CascadeLogStatus.CASCADE, "", msg)
}

/** Log cascade completion summary. */
internal fun CascadingAnalysisEngineImpl.logDone(state: CascadeState) {
    val msg = "Cascade done: ${state.completedCount} completed, " +
        "${state.failedCount} failed, ${state.skippedCount} skipped"
    state.log(CascadeLogStatus.DONE, "", msg)
}
