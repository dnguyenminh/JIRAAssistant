package com.assistant.server.jobs

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.document.DocumentAggregator
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.agent.progress.NoOpProgressReporter
import com.assistant.server.document.InMemorySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Test doubles for JobExecutor subprocess-direct tests. */

class FakeOrchestrator(
    private val successDoc: String? = null,
    private val shouldThrow: Boolean = false
) : BASubprocessOrchestrator(
    subprocessManager = fallbackStubManager(),
    subprocessProxy = fallbackStubProxy(),
    progressReporter = NoOpProgressReporter(),
    settingsRepository = InMemorySettings()
) {
    var called = false

    override suspend fun executeTask(
        config: BATaskConfig
    ): BATaskResult {
        called = true
        if (shouldThrow) throw RuntimeException("crash")
        return if (successDoc != null) {
            BATaskResult(
                document = successDoc, toolCallsExecuted = 1,
                toolCallsFailed = 0, totalDurationMs = 100,
                status = BATaskStatus.SUCCESS
            )
        } else {
            BATaskResult(
                document = "", toolCallsExecuted = 0,
                toolCallsFailed = 0, totalDurationMs = 0,
                status = BATaskStatus.FAILED
            )
        }
    }
}

class TrackingAggregator : DocumentAggregator {
    var called = false
    override suspend fun aggregate(
        ticketId: String
    ): com.assistant.document.models.GenerationContext {
        called = true
        return com.assistant.document.models.GenerationContext(
            mainTicket = com.assistant.kb.KBRecord(
                ticketId = ticketId,
                requirementSummary = "Test",
                evolutionHistory = emptyList(),
                scrumPoints = 1.0,
                confidenceScore = 0.8,
                rationale = "test",
                similarTicketRefs = emptyList(),
                timestamp = "2024-01-01"
            )
        )
    }
}

fun fallbackStubManager() = object : SubprocessManager {
    override suspend fun sendCommand(
        agentType: String, command: String
    ): Flow<String> = emptyFlow()
    override suspend fun isRunning(agentType: String) = false
    override suspend fun terminate(agentType: String) {}
    override suspend fun terminateAll() {}
    override fun getRunningAgentTypes() = emptyList<String>()
}

fun fallbackStubProxy() = object : SubprocessProxy {
    override suspend fun handleToolCallRequest(
        request: ToolCallRequest
    ) = ToolCallResponse(
        id = request.id, success = true
    )
    override fun getAvailableToolDescriptors() =
        emptyList<ToolDescriptor>()
    override fun buildToolListMessage() = ""
    override fun buildToolsUpdatedMessage() = ""
}

// ── Minimal no-op repository fakes ──────────────────────

class NoOpJobRepo : com.assistant.server.db.JobRepository {
    override suspend fun create(job: com.assistant.document.models.GenerationJob) {}
    override suspend fun findById(jobId: String) = null
    override suspend fun findByTicketIdAndTypeActive(ticketId: String, documentType: String) = null
    override suspend fun findActiveByTicketId(ticketId: String) = emptyList<com.assistant.document.models.GenerationJob>()
    override suspend fun findByUser(userId: String, statusFilter: List<String>?) = emptyList<com.assistant.document.models.GenerationJob>()
    override suspend fun findByChainId(chainId: String) = emptyList<com.assistant.document.models.GenerationJob>()
    override suspend fun updateStatus(jobId: String, status: String, progress: Int, phase: String, error: String?) {}
    override suspend fun updateStartedAt(jobId: String, startedAt: String) {}
    override suspend fun findRunningJobs() = emptyList<com.assistant.document.models.GenerationJob>()
}

class NoOpDocRepo : com.assistant.server.db.DocumentRepository {
    override suspend fun save(document: com.assistant.document.models.GeneratedDocument) {}
    override suspend fun findByTicketId(ticketId: String) = emptyList<com.assistant.document.models.GeneratedDocument>()
    override suspend fun findByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun listByTicketId(ticketId: String) = emptyList<com.assistant.server.db.GeneratedDocumentMeta>()
    override suspend fun findLatestByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun findLatestDraftByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun findAllVersions(ticketId: String, documentType: String) = emptyList<com.assistant.server.db.GeneratedDocumentMeta>()
    override suspend fun findByVersion(ticketId: String, documentType: String, versionNumber: Int) = null
    override suspend fun updateApprovalStatus(
        id: Long, status: String, reviewedBy: String?, reviewedAt: String?, rejectReason: String?
    ) {}
    override suspend fun getNextVersionNumber(ticketId: String, documentType: String) = 1
    override suspend fun findById(id: Long) = null
}
