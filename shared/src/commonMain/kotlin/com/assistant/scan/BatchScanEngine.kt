package com.assistant.scan

import com.assistant.ai.AIOrchestrator
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.jira.JiraAttachment
import com.assistant.jira.JiraClient
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import kotlin.coroutines.coroutineContext

/** Exception thrown when a scan conflict occurs. Maps to HTTP 409 Conflict. */
class ScanConflictException(message: String) : RuntimeException(message)

/**
 * Batch Scan Engine — orchestrates per-project ticket scanning using coroutines.
 *
 * State machine: IDLE → SCANNING → PAUSED / COMPLETED / CANCELLED
 * Max 1 concurrent scan per project. Server restart recovery: SCANNING → PAUSED.
 */
class BatchScanEngine(
    internal val aiOrchestrator: AIOrchestrator,
    internal val kbRepository: KBRepository,
    internal val jiraClientProvider: () -> JiraClient,
    internal val featureNetworkMapper: FeatureNetworkMapper,
    internal val scanStateRepository: ScanStateRepository,
    internal val scanLogRepository: ScanLogRepository,
    private val scope: CoroutineScope,
    internal val attachmentProcessor: (suspend (String, String, List<JiraAttachment>) -> Int)? = null,
    /** Called async after scan completes and graph is built. Req: 12.1, 16.1 */
    internal val onScanComplete: (suspend (projectKey: String) -> Unit)? = null,
    /** Called async after each KBRecord is saved during scan. Req: 14.1, 16.1 */
    internal val onKBRecordSaved: (suspend (projectKey: String, record: KBRecord) -> Unit)? = null
) {
    /** Active scan jobs per project — max 1 concurrent scan per project. */
    internal val activeJobs = mutableMapOf<String, Job>()

    /** Configurable parallel batch size — user can change via API. */
    var parallelBatchSize: Int = DEFAULT_CONCURRENCY

    /** AI semaphore — limits concurrent AI inference calls (default 1 for local Ollama). */
    internal var aiSemaphore: Semaphore = Semaphore(DEFAULT_AI_CONCURRENCY)

    /** Whether to force re-analyze tickets already in KB. */
    internal var forceReanalyze: Boolean = false

    suspend fun startScan(projectKey: String, concurrency: Int? = null, aiConcurrency: Int? = null, force: Boolean = false): ScanState {
        if (concurrency != null) parallelBatchSize = concurrency.coerceIn(1, MAX_CONCURRENCY)
        if (aiConcurrency != null) aiSemaphore = Semaphore(aiConcurrency.coerceIn(1, MAX_AI_CONCURRENCY))
        forceReanalyze = force
        checkNoActiveScan(projectKey)
        scanLogRepository.deleteByProjectKey(projectKey)
        val ticketIds = fetchTicketIds(projectKey)
        if (ticketIds.isEmpty()) return handleEmptyProject(projectKey)
        logToBoth(projectKey, "-", ScanLogStatus.COMPLETED,
            "Scan config: ${ticketIds.size} tickets, ×$parallelBatchSize parallel, AI×${aiSemaphore.availablePermits} concurrent" +
            if (forceReanalyze) ", FORCE RE-ANALYZE" else "")
        return launchScan(projectKey, ticketIds)
    }

    suspend fun pauseScan(projectKey: String): ScanState =
        updateScanStatus(projectKey, ScanStatus.PAUSED, cancelJob = true)

    suspend fun resumeScan(projectKey: String): ScanState {
        val current = scanStateRepository.findByProjectKey(projectKey)
            ?: throw IllegalStateException("No scan found for project $projectKey")
        require(current.status == ScanStatus.PAUSED) { "Cannot resume scan in ${current.status} state" }
        val updated = current.copy(status = ScanStatus.SCANNING, updatedAt = Clock.System.now().toString())
        scanStateRepository.save(updated)
        val job = scope.launch { scanLoop(projectKey, startIndex = current.processedCount) }
        activeJobs[projectKey] = job
        return updated
    }

    suspend fun cancelScan(projectKey: String): ScanState =
        updateScanStatus(projectKey, ScanStatus.CANCELLED, cancelJob = true)

    suspend fun getStatus(projectKey: String): ScanState =
        scanStateRepository.findByProjectKey(projectKey) ?: ScanState(
            projectKey = projectKey, status = ScanStatus.IDLE,
            totalTickets = 0, processedCount = 0, currentTicketId = null,
            ticketIds = emptyList(), startedAt = "", updatedAt = ""
        )

    suspend fun getLog(projectKey: String, limit: Int = 50): List<ScanLogEntry> =
        scanLogRepository.getByProjectKey(projectKey, limit.toLong())

    suspend fun clearLog(projectKey: String) =
        scanLogRepository.deleteByProjectKey(projectKey)

    suspend fun getLogPaged(projectKey: String, limit: Int, offset: Int): List<ScanLogEntry> =
        scanLogRepository.getByProjectKeyPaged(projectKey, limit.toLong(), offset.toLong())

    suspend fun getLogCount(projectKey: String): Long =
        scanLogRepository.countByProjectKey(projectKey)

    suspend fun getActiveScans(): List<ScanState> = scanStateRepository.findAllScanning()

    suspend fun recoverOnStartup() {
        for (state in scanStateRepository.findAllScanning()) {
            scanStateRepository.save(state.copy(status = ScanStatus.PAUSED, updatedAt = Clock.System.now().toString()))
        }
    }

    // --- Internal scan loop ---

    internal suspend fun scanLoop(projectKey: String, startIndex: Int) {
        val state = scanStateRepository.findByProjectKey(projectKey) ?: return
        val tickets = state.ticketIds
        var i = startIndex
        while (i < tickets.size) {
            coroutineContext.ensureActive()
            val batchEnd = (i + parallelBatchSize).coerceAtMost(tickets.size)
            val batch = tickets.subList(i, batchEnd)
            processBatchParallel(projectKey, batch, i)
            i = batchEnd
        }
        completeScan(projectKey)
    }

    /** Process a batch of tickets in parallel using coroutines. */
    private suspend fun processBatchParallel(projectKey: String, batch: List<String>, startIdx: Int) {
        kotlinx.coroutines.coroutineScope {
            batch.mapIndexed { idx, ticketId ->
                launch {
                    ensureActive()
                    updateCurrentTicket(projectKey, ticketId)
                    processTicket(projectKey, ticketId)
                }
            }.forEach { it.join() }
        }
        updateProcessedCount(projectKey, startIdx + batch.size)
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 3
        const val MAX_CONCURRENCY = 10
        const val DEFAULT_AI_CONCURRENCY = 1
        const val MAX_AI_CONCURRENCY = 3
    }

    // --- Private helpers ---

    private suspend fun checkNoActiveScan(projectKey: String) {
        val existing = scanStateRepository.findByProjectKey(projectKey)
        if (existing != null && existing.status == ScanStatus.SCANNING) {
            throw ScanConflictException("Scan already running for project $projectKey")
        }
        val activeJob = activeJobs[projectKey]
        if (activeJob != null && activeJob.isActive) {
            throw ScanConflictException("Scan already running for project $projectKey")
        }
    }

    private suspend fun fetchTicketIds(projectKey: String): List<String> {
        val jiraClient = jiraClientProvider()
        println("[BatchScanEngine] startScan: jiraClient type=${jiraClient::class.simpleName} for $projectKey")
        val issues = jiraClient.getIssues(projectKey, maxResults = Int.MAX_VALUE)
        println("[BatchScanEngine] startScan: found ${issues.size} tickets for $projectKey")
        return issues.map { it.key }
    }

    private suspend fun handleEmptyProject(projectKey: String): ScanState {
        val now = Clock.System.now().toString()
        val state = ScanState(projectKey, ScanStatus.COMPLETED, 0, 0, null, emptyList(), now, now)
        scanStateRepository.save(state)
        logToBoth(projectKey, "-", ScanLogStatus.FAILED, "No tickets found in project $projectKey. Try a different project.")
        return state
    }

    private suspend fun launchScan(projectKey: String, ticketIds: List<String>): ScanState {
        val now = Clock.System.now().toString()
        val state = ScanState(projectKey, ScanStatus.SCANNING, ticketIds.size, 0, null, ticketIds, now, now)
        scanStateRepository.save(state)
        activeJobs[projectKey] = scope.launch { scanLoop(projectKey, startIndex = 0) }
        return state
    }

    private suspend fun updateScanStatus(projectKey: String, status: ScanStatus, cancelJob: Boolean): ScanState {
        if (cancelJob) { activeJobs[projectKey]?.cancel(); activeJobs.remove(projectKey) }
        val current = scanStateRepository.findByProjectKey(projectKey)
            ?: throw IllegalStateException("No scan found for project $projectKey")
        val updated = current.copy(status = status, updatedAt = Clock.System.now().toString())
        scanStateRepository.save(updated)
        return updated
    }

    private suspend fun updateCurrentTicket(projectKey: String, ticketId: String) {
        val current = scanStateRepository.findByProjectKey(projectKey) ?: return
        scanStateRepository.save(current.copy(currentTicketId = ticketId, updatedAt = Clock.System.now().toString()))
    }

    private suspend fun updateProcessedCount(projectKey: String, count: Int) {
        val current = scanStateRepository.findByProjectKey(projectKey) ?: return
        scanStateRepository.save(current.copy(processedCount = count, updatedAt = Clock.System.now().toString()))
    }

    private suspend fun completeScan(projectKey: String) {
        val current = scanStateRepository.findByProjectKey(projectKey) ?: return
        // Build graph before marking as completed
        try {
            buildAndSaveGraph(projectKey)
        } catch (e: Exception) {
            logToBoth(projectKey, "-", ScanLogStatus.FAILED,
                "Graph build failed: ${e.message ?: "Unknown error"}")
        }
        // Trigger async indexing after graph is built (does not block scan). Req: 12.1, 16.1
        invokeScanCompleteHook(projectKey)
        scanStateRepository.save(current.copy(
            status = ScanStatus.COMPLETED,
            currentTicketId = null,
            updatedAt = Clock.System.now().toString()
        ))
        activeJobs.remove(projectKey)
    }

    private fun invokeScanCompleteHook(projectKey: String) {
        val hook = onScanComplete ?: return
        scope.launch { runCatching { hook(projectKey) } }
    }

    internal suspend fun logToBoth(projectKey: String, ticketId: String, status: ScanLogStatus, message: String) {
        println("[BatchScanEngine] [$status] $ticketId: $message")
        scanLogRepository.addEntry(ScanLogEntry(projectKey = projectKey, ticketId = ticketId, status = status, message = message, timestamp = Clock.System.now().toString()))
    }
}
