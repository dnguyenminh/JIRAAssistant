package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.GenerationJobDto
import io.ktor.client.statement.*
import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Orchestrates doc generation: POST → poll with InlineProgressRenderer → preview. */
internal object DocumentGenerationFlow {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private var elapsedTimerId: Int? = null
    private const val SECTION_ID = "ti-docgen-section"
    private const val POLL_INTERVAL_MS = 2000L

    @Serializable
    data class GenerateResponse(val jobId: String, val status: String)

    @Serializable
    data class ChainResponse(val chainId: String, val jobs: List<GenerationJobDto>)

    fun startGeneration(ticketId: String, documentType: String) {
        cancelPolling()
        DocGenApiHelper.dismissErrorToast()
        val areaId = DocGenButtonHelper.progressAreaId(documentType)
        scope.launch {
            if (!checkReadiness(documentType)) return@launch
            try {
                val jobId = DocGenApiHelper.postGenerate(ticketId, documentType)
                pollJobUntilComplete(jobId, areaId, ticketId, documentType)
            } catch (e: Exception) {
                stopElapsedTimer()
                DocGenButtonHelper.enableGenerateButton(documentType)
                DocGenApiHelper.showErrorToast(SECTION_ID, e.message ?: "Generation failed")
            } finally {
                DocumentGenerationSection.refreshBadges(ticketId)
            }
        }
    }

    fun startGenerateAll(ticketId: String) {
        cancelPolling()
        DocGenApiHelper.dismissErrorToast()
        scope.launch {
            if (!checkReadiness("BRD")) return@launch
            BlockingOverlay.show(SECTION_ID, "Generating All Documents...")
            try {
                val chain = DocGenApiHelper.postGenerateAll(ticketId)
                for (job in chain.jobs) {
                    BlockingOverlay.remove(SECTION_ID)
                    BlockingOverlay.show(SECTION_ID, "${job.documentType}...")
                    pollChainJob(job.jobId)
                }
            } catch (e: Exception) {
                DocGenApiHelper.showErrorToast(SECTION_ID, e.message ?: "Generate All failed")
            } finally {
                BlockingOverlay.remove(SECTION_ID)
                DocumentGenerationSection.refreshBadges(ticketId)
            }
        }
    }

    fun cancelJob(jobId: String, ticketId: String, documentType: String) {
        BlockingOverlay.show(SECTION_ID, "Đang hủy...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/jobs/$jobId/cancel")
                handleCancelResponse(resp, documentType, ticketId)
            } catch (_: Exception) {
                DocGenApiHelper.showErrorToast(SECTION_ID, "Không thể hủy — thử lại")
                window.setTimeout({ DocGenButtonHelper.enableCancelButton(documentType) }, 2000)
            } finally {
                BlockingOverlay.remove(SECTION_ID)
            }
        }
    }

    fun fetchAndPreview(ticketId: String, docType: String) {
        DocGenApiHelper.dismissErrorToast()
        BlockingOverlay.show(SECTION_ID, "Loading document...")
        scope.launch {
            try {
                val doc = DocGenApiHelper.fetchFullDocument(ticketId, docType)
                if (doc != null) DocumentPreviewPanel.open(doc)
                else DocGenApiHelper.showErrorToast(SECTION_ID, "Not found")
            } catch (e: Exception) {
                DocGenApiHelper.showErrorToast(SECTION_ID, e.message ?: "Failed to load")
            } finally { BlockingOverlay.remove(SECTION_ID) }
        }
    }

    fun fetchDraftAndPreview(ticketId: String, docType: String) {
        DocGenApiHelper.dismissErrorToast()
        BlockingOverlay.show(SECTION_ID, "Loading draft...")
        scope.launch {
            try {
                val doc = DocGenApiHelper.fetchFullDocument(ticketId, docType, "DRAFT")
                if (doc != null) DocumentPreviewPanel.open(doc)
                else DocGenApiHelper.showErrorToast(SECTION_ID, "Not found")
            } catch (e: Exception) {
                DocGenApiHelper.showErrorToast(SECTION_ID, e.message ?: "Failed to load")
            } finally { BlockingOverlay.remove(SECTION_ID) }
        }
    }

    fun cancelPolling() { pollingJob?.cancel(); pollingJob = null; stopElapsedTimer() }

    private suspend fun pollJobUntilComplete(
        jobId: String, areaId: String, ticketId: String, docType: String
    ) {
        var consecutiveErrors = 0
        var timerStarted = false
        for (attempt in 0 until 120) {
            delay(POLL_INTERVAL_MS)
            val job = DocGenApiHelper.fetchJobStatusSafe(jobId)
            if (job == null) {
                if (++consecutiveErrors >= 3) { handlePollFailure(areaId, docType); return }
                continue
            }
            consecutiveErrors = 0
            if (!timerStarted && job.startedAt != null) {
                elapsedTimerId = InlineProgressRenderer.startElapsedTimer(areaId, job.startedAt)
                timerStarted = true
            }
            InlineProgressRenderer.renderProgress(areaId, job)
            val terminal = handleTerminalStatus(job, areaId, ticketId, docType)
            if (terminal) return
        }
        stopElapsedTimer()
        DocGenButtonHelper.enableGenerateButton(docType)
        DocGenApiHelper.showErrorToast(SECTION_ID, "Generation timed out")
    }

    private fun handleTerminalStatus(
        job: GenerationJobDto, areaId: String, ticketId: String, docType: String
    ): Boolean = when (job.status) {
        "COMPLETED" -> { handleCompleted(areaId, ticketId, docType); true }
        "FAILED" -> { handleFailed(areaId, job, ticketId, docType); true }
        "CANCELLED" -> { handleCancelled(areaId, docType); true }
        else -> false
    }

    private suspend fun pollChainJob(jobId: String) {
        for (attempt in 0 until 120) {
            delay(POLL_INTERVAL_MS)
            val job = DocGenApiHelper.fetchJobStatus(jobId)
            when (job.status) {
                "COMPLETED" -> return
                "FAILED" -> throw IllegalStateException(job.errorMessage ?: "Generation failed")
                "CANCELLED" -> throw IllegalStateException("Job cancelled")
            }
        }
        throw IllegalStateException("Generation timed out")
    }

    private fun handleCompleted(areaId: String, ticketId: String, docType: String) {
        stopElapsedTimer()
        InlineProgressRenderer.renderSuccess(areaId) {
            DocGenButtonHelper.enableGenerateButton(docType)
            scope.launch {
                val doc = DocGenApiHelper.fetchFullDocument(ticketId, docType, "DRAFT")
                if (doc != null) DocumentPreviewPanel.open(doc)
            }
        }
    }

    private fun handleFailed(
        areaId: String, job: GenerationJobDto, ticketId: String, docType: String
    ) {
        stopElapsedTimer()
        DocGenButtonHelper.enableGenerateButton(docType)
        InlineProgressRenderer.renderError(areaId, job) { startGeneration(ticketId, docType) }
    }

    private fun handleCancelled(areaId: String, docType: String) {
        stopElapsedTimer()
        InlineProgressRenderer.clearProgress(areaId)
        DocGenButtonHelper.enableGenerateButton(docType)
    }

    private fun handlePollFailure(areaId: String, docType: String) {
        stopElapsedTimer()
        InlineProgressRenderer.clearProgress(areaId)
        DocGenButtonHelper.enableGenerateButton(docType)
        DocGenApiHelper.showErrorToast(SECTION_ID, "Mất kết nối — vui lòng thử lại")
    }

    private suspend fun handleCancelResponse(
        resp: HttpResponse, docType: String, ticketId: String
    ) {
        val areaId = DocGenButtonHelper.progressAreaId(docType)
        when {
            resp.status.value in 200..299 -> {
                cancelPolling()
                InlineProgressRenderer.clearProgress(areaId)
                DocGenButtonHelper.enableGenerateButton(docType)
                DocGenApiHelper.showErrorToast(SECTION_ID, "Job đã được hủy")
            }
            resp.status.value == 409 -> {
                DocGenApiHelper.showErrorToast(SECTION_ID, "Job đã hoàn tất, không thể hủy")
                DocumentGenerationSection.refreshBadges(ticketId)
            }
            else -> DocGenApiHelper.showErrorToast(SECTION_ID, "Không thể hủy — thử lại")
        }
    }

    /** Readiness gate: overlay → check MCP → proceed/cancel/error. */
    private suspend fun checkReadiness(docType: String): Boolean {
        BlockingOverlay.show(SECTION_ID, "Checking MCP tools...")
        return try {
            val proceed = McpReadinessChecker.checkAndProceed(docType)
            if (!proceed) DocGenButtonHelper.enableGenerateButton(docType)
            proceed
        } catch (e: Exception) {
            DocGenApiHelper.showErrorToast(SECTION_ID, "Không thể kiểm tra MCP tools — vui lòng thử lại")
            DocGenButtonHelper.enableGenerateButton(docType)
            false
        } finally {
            BlockingOverlay.remove(SECTION_ID)
        }
    }

    private fun stopElapsedTimer() {
        elapsedTimerId?.let { InlineProgressRenderer.stopElapsedTimer(it) }
        elapsedTimerId = null
    }
}
