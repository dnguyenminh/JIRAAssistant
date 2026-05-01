package com.assistant.server.routes

import com.assistant.ai.AIOrchestrator
import com.assistant.ai.AnalysisPhase
import com.assistant.ai.AnalysisStatus
import com.assistant.jira.JiraClient
import com.assistant.jira.JiraCredentialsService
import com.assistant.rbac.Permission
import com.assistant.server.attachment.AttachmentPipeline
import com.assistant.server.attachment.LinkedAttachmentProcessor
import com.assistant.server.document.TicketGraphHolder
import com.assistant.server.document.jobs.CollectionJobManager
import com.assistant.server.middleware.withPermission
import io.ktor.client.HttpClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import java.util.concurrent.ConcurrentHashMap

/** Response for HTTP 409 Conflict when ticket is being processed by background job (Req 14.4). */
@kotlinx.serialization.Serializable
data class ConflictResponse(val error: String, val ticketId: String)

/**
 * In-memory store for tracking analysis progress per ticket (Req 21.4).
 * Supports 4 phases: FETCHING_JIRA, EXTRACTING_CONTENT, AI_ANALYZING, KB_SYNCING.
 */
object AnalysisStatusTracker {
    private val statuses = ConcurrentHashMap<String, AnalysisStatus>()

    fun update(ticketId: String, phase: String, progressPercent: Int) {
        statuses[ticketId] = AnalysisStatus(ticketId, phase, progressPercent.coerceIn(0, 100))
    }

    /** Update using typed AnalysisPhase with its start percentage. */
    fun updatePhase(ticketId: String, phase: AnalysisPhase) {
        update(ticketId, phase.name, phase.startPercent)
    }

    /** Update using typed AnalysisPhase with custom progress within range. */
    fun updatePhaseProgress(ticketId: String, phase: AnalysisPhase, percent: Int) {
        val clamped = percent.coerceIn(phase.startPercent, phase.endPercent)
        update(ticketId, phase.name, clamped)
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
    val attachmentPipeline by inject<AttachmentPipeline>()
    val credentialsService by inject<JiraCredentialsService>()
    val httpClient by inject<HttpClient>()
    val collectionJobManager by inject<CollectionJobManager>()
    val linkedAttachmentProcessor by inject<LinkedAttachmentProcessor>()
    val ticketGraphHolder by inject<TicketGraphHolder>()
    val jiraClientProvider: () -> JiraClient = { createJiraClientFromDb(credentialsService, httpClient) }

    route("/api/analysis") {
        withPermission(Permission.ANALYZE_AI) {
            get("/{ticketId}") {
                val ticketId = call.parameters["ticketId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))
                // Check if analysis is currently running
                val activeStatus = AnalysisStatusTracker.get(ticketId)
                if (activeStatus != null && activeStatus.phase != AnalysisPhase.COMPLETE.name) {
                    call.respond(HttpStatusCode.Accepted, activeStatus)
                    return@get
                }
                // KB-First: return cached result only, don't trigger new analysis
                val cached = orchestrator.analyzeTicket(ticketId, forceReanalyze = false)
                call.respond(HttpStatusCode.OK, cached)
            }

            get("/{ticketId}/status") {
                val ticketId = call.parameters["ticketId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))
                val status = AnalysisStatusTracker.get(ticketId)
                if (status != null) {
                    call.respond(HttpStatusCode.OK, status)
                } else {
                    // No active analysis — return 404 so frontend can distinguish
                    // "not tracking" from "completed"
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("No active analysis for $ticketId"))
                }
            }

            post("/{ticketId}/reanalyze") {
                val ticketId = call.parameters["ticketId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId is required"))
                val conflict = checkConflict(ticketId, collectionJobManager)
                if (conflict != null) {
                    call.respond(HttpStatusCode.Conflict, conflict)
                    return@post
                }
                preemptPendingItems(ticketId, collectionJobManager)
                // Fire-and-forget: launch analysis async, return 202 immediately
                AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.FETCHING_JIRA)
                CoroutineScope(Dispatchers.IO).launch {
                    runAnalysis(ticketId, orchestrator, forceReanalyze = true,
                        attachmentPipeline = attachmentPipeline, jiraClientProvider = jiraClientProvider,
                        linkedAttachmentProcessor = linkedAttachmentProcessor, ticketGraphHolder = ticketGraphHolder)
                }
                call.respond(HttpStatusCode.Accepted, mapOf("ticketId" to ticketId, "status" to "ACCEPTED"))
            }
        }
    }
}

/**
 * Check if ticket is currently PROCESSING in a LINKED_TICKET_ANALYSIS job (Req 14.4).
 * Returns error response if conflict detected, null if safe to proceed.
 * Attachment processing jobs do NOT block manual analysis (Req 14.3).
 */
private suspend fun checkConflict(
    ticketId: String,
    collectionJobManager: CollectionJobManager
): ConflictResponse? {
    val isProcessing = collectionJobManager.isTicketProcessing(ticketId)
    if (!isProcessing) return null
    return ConflictResponse(
        error = "Ticket đang được phân tích bởi background job, vui lòng chờ hoàn tất",
        ticketId = ticketId
    )
}

/**
 * Preempt PENDING items in active Collection_Jobs for this ticket (Req 14.1).
 * Manual analysis has higher priority than background jobs.
 */
private suspend fun preemptPendingItems(
    ticketId: String,
    collectionJobManager: CollectionJobManager
) {
    collectionJobManager.preemptPendingForTicket(ticketId)
}

/**
 * Execute analysis with 4-phase progress tracking (Req 21.4).
 * Phases: FETCHING_JIRA (0-20%), EXTRACTING_CONTENT (20-35%),
 *         AI_ANALYZING (35-85%), KB_SYNCING (85-100%).
 */
private suspend fun runAnalysis(
    ticketId: String, orchestrator: AIOrchestrator, forceReanalyze: Boolean,
    attachmentPipeline: AttachmentPipeline, jiraClientProvider: () -> JiraClient,
    linkedAttachmentProcessor: LinkedAttachmentProcessor,
    ticketGraphHolder: TicketGraphHolder
): com.assistant.ai.AnalysisResult {
    AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.FETCHING_JIRA)
    try {
        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.EXTRACTING_CONTENT)
        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.AI_ANALYZING)
        val result = orchestrator.analyzeTicket(ticketId, forceReanalyze = forceReanalyze)
        // Only run attachment processing for fresh AI analysis, not KB cache hits
        if (result.source == com.assistant.ai.AnalysisSource.FRESH_AI) {
            AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.KB_SYNCING)
            processTicketAttachments(ticketId, attachmentPipeline, jiraClientProvider)
            processLinkedAttachmentsSynchronously(ticketId, linkedAttachmentProcessor, ticketGraphHolder)
        }
        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.COMPLETE)
        return result
    } finally {
        AnalysisStatusTracker.remove(ticketId)
    }
}

/** Fetch and process attachments for a ticket. Errors are logged but never propagated. */
private suspend fun processTicketAttachments(
    ticketId: String, pipeline: AttachmentPipeline, jiraClientProvider: () -> JiraClient
) {
    try {
        val issue = jiraClientProvider().getIssueDetails(ticketId)
        val attachments = issue?.fields?.attachment.orEmpty()
        if (attachments.isNotEmpty()) {
            val projectKey = ticketId.substringBefore("-")
            pipeline.processAttachments(projectKey, ticketId, attachments)
        }
    } catch (e: Exception) {
        println("[AnalysisRoutes] Attachment processing failed for $ticketId: ${e.message}")
    }
}

/**
 * Process linked ticket attachments synchronously.
 * Takes graph from holder (stored by DeepJiraContentExtractor during extraction).
 * If graph is null (deep extraction not available) or single-node: no-op.
 * Runs synchronously so Ticket Intelligence page blocks until complete.
 * Requirements: 2.1, 3.2, 3.3, 7.4, 7.5
 */
private suspend fun processLinkedAttachmentsSynchronously(
    ticketId: String,
    processor: LinkedAttachmentProcessor,
    holder: TicketGraphHolder
) {
    val graph = holder.take(ticketId) ?: return
    if (graph.nodes.size <= 1) return
    processor.processLinkedAttachments(graph, ticketId, asBackground = false)
}
