package com.assistant.server.routes

import com.assistant.rbac.Permission
import com.assistant.server.document.jobs.CollectionJobRepository
import com.assistant.server.document.models.CollectionJob
import com.assistant.server.document.models.CollectionJobItemResponse
import com.assistant.server.document.models.CollectionJobResponse
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Collection Job API routes — monitor background deep-collection jobs.
 *
 * - GET /api/collection-jobs?ticketId={ticketId} — list jobs for a ticket (Req 13.5)
 * - GET /api/collection-jobs/active — all RUNNING or QUEUED jobs (Req 13.6)
 *
 * Requirements: 13.5, 13.6
 */
fun Routing.collectionJobRoutes() {
    val repository by inject<CollectionJobRepository>()

    route("/api/collection-jobs") {
        withPermission(Permission.VIEW_ANALYSIS) {
            get { handleListByTicket(call, repository) }
            get("/active") { handleListActive(call, repository) }
        }
    }
}

/** GET /api/collection-jobs?ticketId={ticketId} (Req 13.5). */
private suspend fun handleListByTicket(
    call: io.ktor.server.application.ApplicationCall,
    repository: CollectionJobRepository
) {
    val ticketId = call.request.queryParameters["ticketId"]
    if (ticketId.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId query parameter is required"))
        return
    }
    val jobs = repository.findByParentTicketId(ticketId)
    call.respond(HttpStatusCode.OK, jobs.map { it.toResponse() })
}

/** GET /api/collection-jobs/active (Req 13.6). */
private suspend fun handleListActive(
    call: io.ktor.server.application.ApplicationCall,
    repository: CollectionJobRepository
) {
    val jobs = repository.findActive()
    call.respond(HttpStatusCode.OK, jobs.map { it.toResponse() })
}

/** Map domain [CollectionJob] to API [CollectionJobResponse]. */
private fun CollectionJob.toResponse() = CollectionJobResponse(
    jobId = jobId,
    parentTicketId = parentTicketId,
    jobType = jobType.name,
    status = status.name,
    totalItems = totalItems,
    completedItems = completedItems,
    failedItems = failedItems,
    progressPercent = progressPercent,
    items = items.map { it.toResponse() },
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Map domain [CollectionJobItem] to API [CollectionJobItemResponse]. */
private fun com.assistant.server.document.models.CollectionJobItem.toResponse() =
    CollectionJobItemResponse(
        itemId = itemId,
        status = status.name,
        skipReason = skipReason,
        errorMessage = errorMessage
    )
