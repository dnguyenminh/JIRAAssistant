package com.assistant.server.routes

import com.assistant.rbac.Permission
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.JobRepository
import com.assistant.server.jobs.*
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Document generation + versioning + approval routes (Req 8.1–8.7, 6.6, 7.3–7.8).
 */
fun Routing.documentRoutes() {
    val documentRepository by inject<DocumentRepository>()
    val jobManager by inject<JobManager>()
    val jobRepository by inject<JobRepository>()

    route("/api/analysis") {
        withPermission(Permission.ANALYZE_AI) {
            post("/{ticketId}/generate-brd") {
                handleGenerate(call, "BRD", jobManager)
            }
            post("/{ticketId}/generate-fsd") {
                handleGenerate(call, "FSD", jobManager)
            }
            post("/{ticketId}/generate-slides") {
                handleGenerate(call, "REQUIREMENT_SLIDES", jobManager)
            }
            post("/{ticketId}/generate-all") {
                handleGenerateAll(call, jobManager)
            }
            get("/{ticketId}/active-jobs") {
                val ticketId = call.parameters["ticketId"] ?: return@get
                val jobs = jobRepository.findActiveByTicketId(ticketId)
                call.respond(HttpStatusCode.OK, jobs.map { job ->
                    JobResponseDto(
                        jobId = job.jobId, ticketId = job.ticketId,
                        documentType = job.documentType, status = job.status,
                        progressPercent = job.progressPercent, phase = job.phase,
                        chainId = job.chainId, errorMessage = job.errorMessage,
                        startedAt = job.startedAt,
                        phaseLabel = PhaseLabelMapper.getLabel(job.phase)
                    )
                })
            }
            get("/{ticketId}/documents") {
                val ticketId = call.parameters["ticketId"] ?: return@get
                call.respond(HttpStatusCode.OK, documentRepository.listByTicketId(ticketId))
            }
            get("/{ticketId}/documents/{type}") {
                val ticketId = call.parameters["ticketId"] ?: return@get
                val type = call.parameters["type"]?.uppercase() ?: return@get
                handleGetDocumentByType(call, ticketId, type, documentRepository)
            }
            get("/{ticketId}/documents/{type}/versions") {
                val ticketId = call.parameters["ticketId"] ?: return@get
                val type = call.parameters["type"]?.uppercase() ?: return@get
                handleGetVersions(call, ticketId, type, documentRepository)
            }
            get("/{ticketId}/documents/{type}/versions/{n}") {
                val ticketId = call.parameters["ticketId"] ?: return@get
                val type = call.parameters["type"]?.uppercase() ?: return@get
                val n = call.parameters["n"]?.toIntOrNull() ?: return@get
                handleGetVersion(call, ticketId, type, n, documentRepository)
            }
            get("/{ticketId}/documents/{type}/diff") {
                val ticketId = call.parameters["ticketId"] ?: return@get
                val type = call.parameters["type"]?.uppercase() ?: return@get
                handleDiff(call, ticketId, type, documentRepository)
            }
        }
    }

    route("/api/documents") {
        withPermission(Permission.ANALYZE_AI) {
            post("/{documentId}/approve") {
                val docId = call.parameters["documentId"]?.toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid document ID: must be a numeric value"))
                    return@post
                }
                handleApprove(call, docId, documentRepository)
            }
            post("/{documentId}/reject") {
                val docId = call.parameters["documentId"]?.toLongOrNull() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid document ID: must be a numeric value"))
                    return@post
                }
                handleReject(call, docId, documentRepository)
            }
        }
    }
}

private suspend fun handleGenerate(call: ApplicationCall, docType: String, jobManager: JobManager) {
    val ticketId = call.parameters["ticketId"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))
    // Block generation when cascade analysis is still running
    if (CascadeStatusTracker.isRunning(ticketId)) {
        call.respond(HttpStatusCode.Conflict,
            ErrorResponse("Cascade analysis đang chạy cho $ticketId — vui lòng đợi hoàn tất trước khi sinh document"))
        return
    }
    val userId = call.extractUser()?.userId ?: ""
    try {
        val job = jobManager.createJob(ticketId, docType, userId)
        call.respond(HttpStatusCode.Accepted, mapOf("jobId" to job.jobId, "status" to job.status))
    } catch (e: GenerationLockException) {
        call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message, "jobId" to e.existingJobId))
    } catch (e: DependencyException) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Dependency error"))
    }
}

private suspend fun handleGenerateAll(call: ApplicationCall, jobManager: JobManager) {
    val ticketId = call.parameters["ticketId"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))
    val userId = call.extractUser()?.userId ?: ""
    try {
        val chain = jobManager.createChain(ticketId, userId)
        call.respond(HttpStatusCode.Accepted, chain)
    } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(e.message ?: "Chain creation failed"))
    }
}

/** Simple principal extraction helper. */
private fun ApplicationCall.extractUser(): UserPrincipal? {
    return try {
        val jwt = principal<JWTPrincipal>()
        jwt?.let { UserPrincipal(it.payload.getClaim("email")?.asString() ?: "") }
    } catch (_: Exception) { null }
}

private data class UserPrincipal(val userId: String)
