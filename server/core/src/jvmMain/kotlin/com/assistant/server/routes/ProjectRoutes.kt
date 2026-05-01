package com.assistant.server.routes

import com.assistant.ai.AIOrchestrator
import com.assistant.ai.ProjectAnalysisResponse
import com.assistant.jira.*
import com.assistant.kb.KBRepository
import com.assistant.rbac.Permission
import com.assistant.scan.BatchScanEngine
import com.assistant.scan.ScanStatus
import com.assistant.scan.TicketAnalysisState
import com.assistant.scan.TicketAnalysisStatus
import com.assistant.server.middleware.withPermission
import io.ktor.client.HttpClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.Base64

/**
 * Project routes — authenticated access.
 * Creates JiraClient on each request from DB credentials to ensure
 * config changes via Integrations page take effect immediately.
 */
fun Routing.projectRoutes() {
    val orchestrator by inject<AIOrchestrator>()
    val credentialsService by inject<JiraCredentialsService>()
    val httpClient by inject<HttpClient>()
    val kbRepository by inject<KBRepository>()
    val batchScanEngine by inject<BatchScanEngine>()

    authenticate("auth-jwt") {
        route("/api/projects") {
            get {
                val jiraClient = createJiraClientFromDb(credentialsService, httpClient)
                if (jiraClient is NoOpJiraClient) {
                    application.log.info("[ProjectRoutes] Jira not configured — returning empty project list")
                }
                val projects = jiraClient.getProjects()
                if (projects.isEmpty()) {
                    application.log.info("[ProjectRoutes] getProjects returned empty list")
                }
                call.respond(HttpStatusCode.OK, projects)
            }

            get("/{key}/issues") {
                val projectKey = call.parameters["key"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))

                val jiraClient = createJiraClientFromDb(credentialsService, httpClient)
                val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 50
                val issues = jiraClient.getIssues(projectKey, maxResults)
                if (issues.isEmpty()) {
                    application.log.info("[ProjectRoutes] getIssues returned empty for project $projectKey")
                }
                call.respond(HttpStatusCode.OK, issues)
            }

            get("/{key}/analysis") {
                val projectKey = call.parameters["key"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))

                val jiraClient = createJiraClientFromDb(credentialsService, httpClient)
                val issues = jiraClient.getIssues(projectKey, maxResults = Int.MAX_VALUE)
                val providerStatuses = orchestrator.getProviderStatuses()

                val totalTickets = issues.size
                val resolvedCount = issues.count { it.fields.resolution != null }
                val resolutionRate = calculateResolutionRate(issues)
                val cycleTimeDays = calculateCycleTimeDays(issues)
                val blockedCount = countBlockedTickets(issues)

                val aiVelocity = orchestrator.calculateAIVelocity(totalTickets, resolvedCount, cycleTimeDays)
                val velocityTrend = orchestrator.generateVelocityTrend(totalTickets, resolvedCount)
                val bottlenecks = orchestrator.analyzeBottlenecks(totalTickets, resolvedCount, cycleTimeDays, blockedCount)

                call.respond(HttpStatusCode.OK, ProjectAnalysisResponse(
                    projectKey = projectKey,
                    totalTickets = totalTickets,
                    resolutionRate = resolutionRate,
                    cycleTimeDays = cycleTimeDays,
                    aiVelocity = aiVelocity,
                    velocityTrend = velocityTrend,
                    bottlenecks = bottlenecks,
                    providerStatuses = providerStatuses
                ))
            }
        }
    }

    // Ticket analysis status — requires VIEW_ANALYSIS (Reader+)
    withPermission(Permission.VIEW_ANALYSIS) {
        get("/api/projects/{key}/tickets/status") {
            val projectKey = call.parameters["key"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))

            val jiraClient = createJiraClientFromDb(credentialsService, httpClient)
            val issues = jiraClient.getIssues(projectKey, maxResults = Int.MAX_VALUE)

            val scanState = batchScanEngine.getStatus(projectKey)
            val isScanning = scanState.status == ScanStatus.SCANNING

            val statuses = issues.map { issue ->
                val ticketId = issue.key
                val kbRecord = kbRepository.findByTicketId(ticketId)
                val ticketUpdatedAt = issue.fields.updated

                val analysisState = when {
                    isScanning && scanState.currentTicketId == ticketId -> TicketAnalysisState.ANALYZING
                    kbRecord != null && ticketUpdatedAt != null && isUpdatedAfterAnalysis(kbRecord.timestamp, ticketUpdatedAt) -> TicketAnalysisState.HAS_UPDATES
                    kbRecord != null && hasDeepAnalysis(kbRecord) -> TicketAnalysisState.ANALYZED
                    kbRecord != null -> TicketAnalysisState.SCANNED
                    else -> TicketAnalysisState.NOT_ANALYZED
                }

                TicketAnalysisStatus(
                    ticketId = ticketId,
                    ticketSummary = issue.fields.summary,
                    analysisState = analysisState,
                    lastAnalyzedAt = kbRecord?.timestamp,
                    ticketUpdatedAt = ticketUpdatedAt
                )
            }

            call.respond(HttpStatusCode.OK, statuses)
        }
    }
}

/**
 * Create a JiraClient by reading credentials from DB via JiraCredentialsService.
 * Returns NoOpJiraClient if Jira is not configured.
 */
fun createJiraClientFromDb(credentialsService: JiraCredentialsService, httpClient: HttpClient): JiraClient {
    val credentials = credentialsService.getJiraCredentials() ?: return NoOpJiraClient()
    val token = Base64.getEncoder().encodeToString("${credentials.email}:${credentials.apiToken}".toByteArray())
    return JiraRestClient(httpClient, credentials.domain, "Basic $token")
}

private fun calculateResolutionRate(issues: List<JiraIssue>): Double {
    if (issues.isEmpty()) return 0.0
    val resolved = issues.count { it.fields.resolution != null }
    return (resolved.toDouble() / issues.size) * 100.0
}

private fun calculateCycleTimeDays(issues: List<JiraIssue>): Double {
    val resolvedIssues = issues.filter { it.fields.resolution != null && it.fields.created != null && it.fields.updated != null }
    if (resolvedIssues.isEmpty()) return 0.0
    val totalDays = resolvedIssues.sumOf { issue ->
        try {
            val created = parseIsoDate(issue.fields.created!!)
            val updated = parseIsoDate(issue.fields.updated!!)
            val diffMs = updated - created
            (diffMs / (1000.0 * 60 * 60 * 24)).coerceAtLeast(0.0)
        } catch (_: Exception) { 0.0 }
    }
    return totalDays / resolvedIssues.size
}

private fun countBlockedTickets(issues: List<JiraIssue>): Int {
    return issues.count { issue ->
        val statusName = (issue.fields.status?.name ?: "").lowercase()
        statusName.contains("block") || statusName.contains("impediment") || statusName.contains("on hold")
    }
}

private fun parseIsoDate(dateStr: String): Long {
    return try {
        java.time.Instant.parse(dateStr).toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.LocalDate.parse(dateStr.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) { 0L }
    }
}

/**
 * Check if a ticket was updated after its last analysis.
 * @param analysisTimestamp epoch millis string from KBRecord.timestamp
 * @param ticketUpdatedAt ISO date string from Jira issue updated field
 */
private fun isUpdatedAfterAnalysis(analysisTimestamp: String, ticketUpdatedAt: String): Boolean {
    val analysisMs = analysisTimestamp.toLongOrNull() ?: return false
    val updatedMs = parseIsoDate(ticketUpdatedAt)
    if (updatedMs == 0L) return false
    return updatedMs > analysisMs
}

/** Check if KB record has deep analysis data (not just basic batch scan). */
private fun hasDeepAnalysis(record: com.assistant.kb.KBRecord): Boolean {
    return record.businessSummary.isNotBlank() ||
        record.technicalDetails.apiSpecifications.isNotEmpty() ||
        record.acceptanceCriteria.isNotEmpty()
}
