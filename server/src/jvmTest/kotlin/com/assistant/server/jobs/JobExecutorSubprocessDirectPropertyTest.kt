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
import com.assistant.document.models.GeneratedDocument
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.GeneratedDocumentMeta
import com.assistant.settings.SettingsRepository
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 2: JobExecutor subprocess-direct save.
 *
 * For any valid job parameters where subprocess returns SUCCESS,
 * the document is saved directly with
 * aiProviderUsed = "BA Subprocess Orchestrator"
 * and no AIAgent.analyze() call is made.
 *
 * **Validates: Requirements 5.1, 5.2, 5.4, 5.6**
 *
 * Feature: legacy-pipeline-removal, Property 2: JobExecutor subprocess-direct save
 */
@Tag("legacy-pipeline-removal")
class JobExecutorSubprocessDirectPropertyTest {

    @Test
    fun `Property 2 - subprocess success saves with correct provider`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.string(minSize = 1, maxSize = 12),
            Arb.string(minSize = 1, maxSize = 12),
            Arb.element("BRD", "FSD")
        ) { jobId, ticketId, docType ->
            val docRepo = PropCapturingDocRepo()
            val orch = PropFakeOrchestrator(docType)
            val exec = buildExecutor(orch, docRepo)

            exec.execute(jobId, ticketId, docType)

            assertTrue(orch.called, "Orchestrator must be called")
            assertEquals(1, docRepo.savedDocs.size, "Exactly one doc saved")
            assertEquals(
                "BA Subprocess Orchestrator",
                docRepo.savedDocs[0].aiProviderUsed,
                "aiProviderUsed must be subprocess stub name"
            )
            assertEquals(docType, docRepo.savedDocs[0].documentType)
            assertEquals(ticketId, docRepo.savedDocs[0].ticketId)
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildExecutor(
        orch: BASubprocessOrchestrator,
        docRepo: DocumentRepository
    ) = JobExecutor(
        aggregator = TrackingAggregator(),
        documentRepository = docRepo,
        jobRepository = NoOpJobRepo(),
        settingsRepository = null,
        subprocessOrchestrator = orch
    )
}

// ── Test doubles ─────────────────────────────────────────────

/** Orchestrator returning a parseable BRD/FSD document. */
private class PropFakeOrchestrator(
    private val docType: String
) : BASubprocessOrchestrator(
    subprocessManager = PropStubManager(),
    subprocessProxy = PropStubProxy(),
    progressReporter = PropNoOpReporter(),
    settingsRepository = PropStubSettings()
) {
    var called = false

    override suspend fun executeTask(config: BATaskConfig): BATaskResult {
        called = true
        val doc = buildMinimalDoc(docType)
        return BATaskResult(
            document = doc,
            toolCallsExecuted = 1,
            toolCallsFailed = 0,
            totalDurationMs = 50,
            status = BATaskStatus.SUCCESS
        )
    }

    private fun buildMinimalDoc(type: String): String = when (type) {
        "BRD" -> "# BRD\n## Revision History\nv1.0\n## Introduction\nTest"
        else -> "# FSD\n## Revision History\nv1.0\n## Introduction\nTest"
    }
}

private class PropCapturingDocRepo : DocumentRepository {
    val savedDocs = mutableListOf<GeneratedDocument>()
    override suspend fun save(document: GeneratedDocument) { savedDocs.add(document) }
    override suspend fun findByTicketId(ticketId: String) = emptyList<GeneratedDocument>()
    override suspend fun findByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun listByTicketId(ticketId: String) = emptyList<GeneratedDocumentMeta>()
    override suspend fun findLatestByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun findLatestDraftByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun findAllVersions(ticketId: String, documentType: String) = emptyList<GeneratedDocumentMeta>()
    override suspend fun findByVersion(ticketId: String, documentType: String, versionNumber: Int) = null
    override suspend fun updateApprovalStatus(
        id: Long, status: String, reviewedBy: String?, reviewedAt: String?, rejectReason: String?
    ) {}
    override suspend fun getNextVersionNumber(ticketId: String, documentType: String) = 1
    override suspend fun findById(id: Long) = null
}

private class PropNoOpReporter : ProgressReporter {
    override suspend fun reportPhase(phaseName: String, phaseIndex: Int, totalPhases: Int) {}
    override suspend fun reportProgress(percent: Int, message: String) {}
    override suspend fun reportToolCall(toolName: String, status: String) {}
}

private class PropStubManager : SubprocessManager {
    override suspend fun sendCommand(agentType: String, command: String) = emptyFlow<String>()
    override suspend fun isRunning(agentType: String) = false
    override suspend fun terminate(agentType: String) {}
    override suspend fun terminateAll() {}
    override fun getRunningAgentTypes() = emptyList<String>()
}

private class PropStubProxy : SubprocessProxy {
    override suspend fun handleToolCallRequest(request: ToolCallRequest) =
        ToolCallResponse(request.id, true, "ok")
    override fun getAvailableToolDescriptors() = emptyList<ToolDescriptor>()
    override fun buildToolListMessage() = ""
    override fun buildToolsUpdatedMessage() = ""
}

private class PropStubSettings : SettingsRepository {
    override suspend fun getAll() = emptyMap<String, String>()
    override suspend fun get(key: String): String? = null
    override suspend fun put(key: String, value: String) {}
    override suspend fun putAll(settings: Map<String, String>) {}
}
