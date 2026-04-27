package com.assistant.server.routes

import com.assistant.document.models.GenerationJob
import com.assistant.rbac.Permission
import com.assistant.server.db.JobRepository
import com.assistant.server.jobs.*
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class JobResponseDto(
    val jobId: String,
    val ticketId: String,
    val documentType: String,
    val status: String,
    val progressPercent: Int = 0,
    val phase: String = "QUEUED",
    val chainId: String? = null,
    val errorMessage: String? = null,
    val startedAt: String? = null,
    val phaseLabel: String? = null
)

/**
 * Job management routes — list, detail, pause, resume, cancel (Req 2.5–2.6, 3.1–3.6).
 */
fun Routing.jobRoutes() {
    val jobRepository by inject<JobRepository>()
    val jobManager by inject<JobManager>()

    route("/api/jobs") {
        withPermission(Permission.ANALYZE_AI) {
            get {
                val status = call.request.queryParameters["status"] ?: "active"
                val filter = when (status) {
                    "active" -> listOf("QUEUED", "RUNNING", "PAUSED")
                    "completed" -> listOf("COMPLETED", "FAILED", "CANCELLED")
                    else -> null
                }
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("email")?.asString() ?: ""
                val jobs = jobRepository.findByUser(userId, filter)
                call.respond(HttpStatusCode.OK, jobs.map { jobResponse(it) })
            }
            get("/{jobId}") {
                val jobId = call.parameters["jobId"] ?: return@get
                val job = jobRepository.findById(jobId)
                if (job != null) call.respond(HttpStatusCode.OK, jobResponse(job))
                else call.respond(HttpStatusCode.NotFound, ErrorResponse("Job not found"))
            }
            post("/{jobId}/pause") {
                val jobId = call.parameters["jobId"] ?: return@post
                try {
                    val job = jobManager.pauseJob(jobId)
                    call.respond(HttpStatusCode.OK, job)
                } catch (e: JobNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: ""))
                } catch (e: InvalidTransitionException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: ""))
                }
            }
            post("/{jobId}/resume") {
                val jobId = call.parameters["jobId"] ?: return@post
                try {
                    val job = jobManager.resumeJob(jobId)
                    call.respond(HttpStatusCode.OK, job)
                } catch (e: JobNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: ""))
                } catch (e: InvalidTransitionException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: ""))
                }
            }
            post("/{jobId}/cancel") {
                val jobId = call.parameters["jobId"] ?: return@post
                try {
                    val job = jobManager.cancelJob(jobId)
                    call.respond(HttpStatusCode.OK, job)
                } catch (e: JobNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: ""))
                } catch (e: InvalidTransitionException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: ""))
                }
            }
        }
    }
}

/**
 * Converts a GenerationJob to a response map including computed phaseLabel.
 */
private fun jobResponse(job: GenerationJob): JobResponseDto = JobResponseDto(
    jobId = job.jobId,
    ticketId = job.ticketId,
    documentType = job.documentType,
    status = job.status,
    progressPercent = job.progressPercent,
    phase = job.phase,
    chainId = job.chainId,
    errorMessage = job.errorMessage,
    startedAt = job.startedAt,
    phaseLabel = PhaseLabelMapper.getLabel(job.phase)
)
