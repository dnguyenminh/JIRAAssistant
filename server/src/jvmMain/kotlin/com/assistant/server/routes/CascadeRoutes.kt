package com.assistant.server.routes

import com.assistant.ai.deepanalysis.CascadingAnalysisEngine
import com.assistant.ai.deepanalysis.models.CascadeResult
import com.assistant.ai.deepanalysis.models.CascadeStatus
import com.assistant.rbac.Permission
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

/**
 * Cascade routes — POST /api/analysis/{ticketId}/cascade (start),
 * GET /api/analysis/{ticketId}/cascade/status (poll progress).
 * Requires ANALYZE_AI permission (same as AnalysisRoutes).
 *
 * Requirements: 26.8, 26.10
 */
fun Routing.cascadeRoutes() {
    val cascadeEngine by inject<CascadingAnalysisEngine>()

    route("/api/analysis") {
        withPermission(Permission.ANALYZE_AI) {
            post("/{ticketId}/cascade") {
                val ticketId = extractTicketId(call) ?: return@post
                if (CascadeStatusTracker.isRunning(ticketId)) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Cascade already running for $ticketId"))
                    return@post
                }
                launchCascade(ticketId, cascadeEngine)
                val initial = initialRunningResult(ticketId)
                call.respond(HttpStatusCode.Accepted, initial)
            }

            get("/{ticketId}/cascade/status") {
                val ticketId = extractTicketId(call) ?: return@get
                val result = CascadeStatusTracker.get(ticketId)
                    ?: CascadeResult(status = CascadeStatus.IDLE)
                call.respond(HttpStatusCode.OK, result)
            }
        }
    }
}

/** Extract ticketId path param or respond 400. */
private suspend fun extractTicketId(call: ApplicationCall): String? {
    val ticketId = call.parameters["ticketId"]
    if (ticketId == null) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))
    }
    return ticketId
}

/** Launch cascade asynchronously and track status. */
private fun launchCascade(ticketId: String, engine: CascadingAnalysisEngine) {
    val running = initialRunningResult(ticketId)
    CascadeStatusTracker.update(ticketId, running)
    CoroutineScope(Dispatchers.Default).launch {
        runCascadeSafe(ticketId, engine)
    }
}

/** Run cascade with error handling, update tracker on completion. */
private suspend fun runCascadeSafe(ticketId: String, engine: CascadingAnalysisEngine) {
    try {
        val result = engine.cascade(ticketId)
        CascadeStatusTracker.update(ticketId, result)
    } catch (e: Exception) {
        val failed = CascadeResult(status = CascadeStatus.FAILED)
        CascadeStatusTracker.update(ticketId, failed)
    }
}

/** Create initial RUNNING result for immediate response. */
private fun initialRunningResult(ticketId: String): CascadeResult {
    return CascadeResult(
        status = CascadeStatus.RUNNING,
        totalTickets = 0,
        completedTickets = 0,
        failedTickets = 0
    )
}
