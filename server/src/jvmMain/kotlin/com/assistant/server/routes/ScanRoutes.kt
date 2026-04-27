package com.assistant.server.routes

import com.assistant.rbac.Permission
import com.assistant.scan.*
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

// --- DTOs ---

@Serializable
data class ScanStatusResponse(
    val projectKey: String,
    val status: ScanStatus,
    val totalTickets: Int,
    val processedCount: Int,
    val progressPercent: Int,
    val currentTicketId: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val recentLog: List<ScanLogEntryResponse> = emptyList(),
    val aiReady: Boolean = true
)

@Serializable
data class ScanLogEntryResponse(
    val id: Long,
    val ticketId: String,
    val status: String,
    val message: String,
    val timestamp: String
)

@Serializable
data class ScanLogResponse(
    val projectKey: String,
    val entries: List<ScanLogEntryResponse>,
    val totalCount: Int
)

@Serializable
data class AIStatusResponse(
    val aiReady: Boolean,
    val activeCount: Int,
    val totalCount: Int
)

// --- Extension functions for DTO mapping ---

fun ScanState.toResponse(recentLog: List<ScanLogEntry> = emptyList()) = ScanStatusResponse(
    projectKey = projectKey,
    status = status,
    totalTickets = totalTickets,
    processedCount = processedCount,
    progressPercent = progressPercent,
    currentTicketId = currentTicketId,
    startedAt = startedAt.ifBlank { null },
    updatedAt = updatedAt.ifBlank { null },
    recentLog = recentLog.map { it.toResponse() }
)

fun ScanLogEntry.toResponse() = ScanLogEntryResponse(
    id = id,
    ticketId = ticketId,
    status = status.name,
    message = message,
    timestamp = timestamp
)

// --- ConflictException ---

/**
 * HTTP 409 Conflict exception for use in route handlers.
 */
class ConflictException(message: String) : RuntimeException(message)

// --- Routes ---

/**
 * Scan routes — manage batch scan lifecycle for a project.
 * POST start/pause/resume/cancel require ANALYZE_AI (Neural_Architect+).
 * GET status/log require VIEW_ANALYSIS (Reader+).
 */
fun Routing.scanRoutes() {
    val batchScanEngine by inject<BatchScanEngine>()
    val aiOrchestrator by inject<com.assistant.ai.AIOrchestrator>()
    val settingsRepo by inject<com.assistant.settings.SettingsRepository>()

    // Cross-project: active scans endpoint
    route("/api/scan") {
        withPermission(Permission.VIEW_ANALYSIS) {
            get("/active") {
                val activeScans = batchScanEngine.getActiveScans()
                call.respond(HttpStatusCode.OK, activeScans.map { it.toResponse() })
            }
        }
    }

    route("/api/projects/{key}/scan") {
        // START, PAUSE, RESUME, CANCEL — requires ANALYZE_AI permission
        withPermission(Permission.ANALYZE_AI) {
            // AI readiness check before scan
            get("/ai-status") {
                val statuses = aiOrchestrator.getProviderStatuses()
                val hasActive = statuses.any { it.status == com.assistant.ai.ConnectionStatus.ACTIVE }
                call.respond(HttpStatusCode.OK, AIStatusResponse(
                    aiReady = hasActive,
                    activeCount = statuses.count { it.status == com.assistant.ai.ConnectionStatus.ACTIVE },
                    totalCount = statuses.size
                ))
            }

            post("/start") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                if (projectKey.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key cannot be empty"))
                }
                val concurrency = call.request.queryParameters["concurrency"]?.toIntOrNull()
                val aiConcurrency = call.request.queryParameters["aiConcurrency"]?.toIntOrNull()
                val forceReanalyze = call.request.queryParameters["forceReanalyze"]?.toBoolean() ?: false
                // Save batch_prompt_size to settings if provided. Req: AC 35, AC 43
                val batchPromptSize = call.request.queryParameters["batchPromptSize"]?.toIntOrNull()
                if (batchPromptSize != null && batchPromptSize >= 1) {
                    settingsRepo.put("batch_prompt_size", batchPromptSize.toString())
                }
                application.log.info("[ScanRoutes] Starting scan for project: $projectKey (concurrency: ${concurrency ?: "default"}, ai: ${aiConcurrency ?: "default"}, force: $forceReanalyze, batchPrompt: ${batchPromptSize ?: "default"})")
                try {
                    val state = batchScanEngine.startScan(projectKey, concurrency, aiConcurrency, forceReanalyze)
                    val logEntries = batchScanEngine.getLog(projectKey, limit = 5)
                    call.respond(HttpStatusCode.OK, state.toResponse(logEntries))
                } catch (e: ScanConflictException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Scan already running"))
                } catch (e: Exception) {
                    application.log.error("[ScanRoutes] Scan start failed for $projectKey: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Scan failed: ${e.message}"))
                }
            }

            post("/pause") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.pauseScan(projectKey)
                call.respond(HttpStatusCode.OK, state.toResponse())
            }

            post("/resume") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.resumeScan(projectKey)
                call.respond(HttpStatusCode.OK, state.toResponse())
            }

            post("/cancel") {
                val projectKey = call.parameters["key"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.cancelScan(projectKey)
                call.respond(HttpStatusCode.OK, state.toResponse())
            }
        }

        // STATUS & LOG — requires VIEW_ANALYSIS permission (Reader+)
        withPermission(Permission.VIEW_ANALYSIS) {
            get("/status") {
                val projectKey = call.parameters["key"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val state = batchScanEngine.getStatus(projectKey)
                val recentLog = batchScanEngine.getLog(projectKey, limit = 50)
                call.respond(HttpStatusCode.OK, state.toResponse(recentLog))
            }

            get("/log") {
                val projectKey = call.parameters["key"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val entries = if (offset > 0) {
                    batchScanEngine.getLogPaged(projectKey, limit, offset)
                } else {
                    batchScanEngine.getLog(projectKey, limit)
                }
                val totalCount = batchScanEngine.getLogCount(projectKey)
                call.respond(HttpStatusCode.OK, ScanLogResponse(
                    projectKey = projectKey,
                    entries = entries.map { it.toResponse() },
                    totalCount = totalCount.toInt()
                ))
            }
        }

        // DELETE log — requires ANALYZE_AI (Neural_Architect+)
        withPermission(Permission.ANALYZE_AI) {
            delete("/log") {
                val projectKey = call.parameters["key"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("project key is required"))
                batchScanEngine.clearLog(projectKey)
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "projectKey" to projectKey))
            }
        }
    }
}
