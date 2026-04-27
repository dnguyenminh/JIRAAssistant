package com.assistant.ai.deepanalysis

import com.assistant.ai.AIOrchestrator
import com.assistant.ai.deepanalysis.models.*
import com.assistant.kb.KBRepository
import kotlinx.coroutines.sync.Semaphore

/**
 * BFS-based cascading analysis engine.
 *
 * After analyzing a seed ticket, discovers related tickets
 * (issue links, sub-tasks, parent, comment mentions),
 * checks KB, and analyzes missing ones recursively.
 *
 * Sequential processing with shared AI semaphore.
 * Safety limit prevents runaway cascading.
 *
 * Requirements: 26.1-26.7
 */
class CascadingAnalysisEngineImpl(
    internal val aiOrchestrator: AIOrchestrator,
    internal val jiraContentExtractor: JiraContentExtractor,
    internal val kbRepository: KBRepository,
    internal val aiSemaphore: Semaphore,
    private val maxTickets: Int = DEFAULT_MAX_TICKETS
) : CascadingAnalysisEngine {

    override suspend fun cascade(ticketId: String): CascadeResult {
        val state = CascadeState(maxTickets)
        state.enqueue(ticketId)
        state.log(CascadeLogStatus.CASCADE, ticketId, "Cascade started from $ticketId")
        processBfsQueue(state)
        return buildResult(state)
    }

    /** BFS loop: dequeue → analyze → discover → enqueue. Req 26.5 */
    private suspend fun processBfsQueue(state: CascadeState) {
        while (state.hasNext()) {
            if (state.isLimitReached()) {
                logLimitReached(state)
                break
            }
            val current = state.dequeue()
            processOneTicket(current, state)
        }
        logDone(state)
    }

    companion object {
        const val DEFAULT_MAX_TICKETS = 50
    }
}
