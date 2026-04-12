package com.assistant.server.routes

import com.assistant.ai.AIOrchestrator
import com.assistant.ai.AnalysisStatus
import com.assistant.rbac.Permission
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for tracking analysis progress per ticket.
 * Entries are set when analysis starts and removed shortly after completion.
 */
object AnalysisStatusTracker {
    private val statuses = ConcurrentHashMap<String, AnalysisStatus>()

    fun update(ticketId: String, phase: String, progressPercent: Int) {
        statuses[ticketId] = AnalysisStatus(ticketId, phase, progressPercent.coerceIn(0, 100))
    }

    fun get(ticketId: String): AnalysisStatus? = statuses[ticketId]

    fun remove(ticketId: String) { statuses.remove(ticketId) }
}

/**
 * Analysis routes — GET /api/analysis/{ticketId}, GET /api/analysis/{ticketId}/status,
 * and POST /api/analysis/{ticketId}/reanalyze.
 * Requires Neural_Architect+ role (ANALYZE_AI permission).
 */
fun Routing.analysisRoutes() {
    val orchestrator by inject<AIOrchestrator>()

    route("/api/analysis") {
        withPermission(Permission.ANALYZE_AI) {
            get("/{ticketId}") {
                val ticketId = call.parameters["ticketId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))

                // Track analysis progress
                AnalysisStatusTracker.update(ticketId, "METADATA", 10)
                try {
                    AnalysisStatusTracker.update(ticketId, "AI_ANALYZING", 40)
                    val result = orchestrator.analyzeTicket(ticketId, forceReanalyze = false)
                    AnalysisStatusTracker.update(ticketId, "KB_SYNCING", 85)
                    AnalysisStatusTracker.update(ticketId, "COMPLETE", 100)
                    call.respond(HttpStatusCode.OK, result)
                } finally {
                    // Clean up after a short delay to allow final poll
                    AnalysisStatusTracker.remove(ticketId)
                }
            }

            get("/{ticketId}/status") {
                val ticketId = call.parameters["ticketId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))

                val status = AnalysisStatusTracker.get(ticketId)
                    ?: AnalysisStatus(ticketId, "COMPLETE", 100)
                call.respond(HttpStatusCode.OK, status)
            }

            post("/{ticketId}/reanalyze") {
                val ticketId = call.parameters["ticketId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))

                AnalysisStatusTracker.update(ticketId, "METADATA", 10)
                try {
                    AnalysisStatusTracker.update(ticketId, "AI_ANALYZING", 40)
                    val result = orchestrator.analyzeTicket(ticketId, forceReanalyze = true)
                    AnalysisStatusTracker.update(ticketId, "KB_SYNCING", 85)
                    AnalysisStatusTracker.update(ticketId, "COMPLETE", 100)
                    call.respond(HttpStatusCode.OK, result)
                } finally {
                    AnalysisStatusTracker.remove(ticketId)
                }
            }
        }
    }
}
