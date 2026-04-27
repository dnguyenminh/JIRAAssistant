package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GeneratedDocumentMeta
import com.assistant.frontend.models.GenerationJobDto
import com.assistant.rbac.Permission
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/** Renders DOCUMENT GENERATION section. Req: 4.5, 8.1, 8.2, 8.4 */
internal object DocumentGenerationSection {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var existingDocs: List<GeneratedDocumentMeta> = emptyList()
    private var activeJobs: List<GenerationJobDto> = emptyList()
    private var cascadeUnlockJob: Job? = null

    fun render(ticketId: String, isAnalyzed: Boolean, isReader: Boolean) {
        val section = getSection()
        if (!isAnalyzed) { section?.style?.display = "none"; return }
        section?.style?.display = "block"
        if (isReader) disableAllButtons() else enableAllButtons()
        applyRBAC()
        fetchActiveJobsAndDocs(ticketId)
        bindButtons(ticketId)
    }

    fun hide() { getSection()?.style?.display = "none" }

    fun refreshBadges(ticketId: String) { fetchActiveJobsAndDocs(ticketId) }

    private fun fetchActiveJobsAndDocs(ticketId: String) {
        scope.launch {
            try {
                val cascadeRunning = checkCascadeRunning(ticketId)
                fetchActiveJobs(ticketId)
                fetchExistingDocuments(ticketId)
                applyGenerationLock()
                recoverTerminalButtonStates()
                applyDependencyTooltips()
                if (cascadeRunning) { applyCascadeLock(); startCascadeUnlockPolling(ticketId) }
                else removeCascadeLock()
            } catch (e: Exception) {
                console.log("[DocGenSection] fetch error: ${e.message}")
            }
        }
    }

    private fun recoverTerminalButtonStates() {
        for (job in activeJobs) {
            if (DocGenButtonHelper.shouldEnableButton(job.status)) {
                val btnId = DocGenButtonHelper.buttonIdForDocType(job.documentType) ?: continue
                val btn = document.getElementById(btnId) as? HTMLElement ?: continue
                DocGenButtonHelper.enableButton(btn)
                restoreButtonLabel(btn, job.documentType)
            }
        }
    }

    private fun restoreButtonLabel(btn: HTMLElement, docType: String) {
        val hasDocs = existingDocs.any { it.documentType == docType }
        btn.textContent = when (docType) {
            "BRD" -> if (hasDocs) "RE-GENERATE BRD" else "GENERATE BRD"
            "FSD" -> if (hasDocs) "RE-GENERATE FSD" else "GENERATE FSD"
            else -> "GENERATE SLIDES"
        }
    }

    private suspend fun checkCascadeRunning(ticketId: String): Boolean {
        return try {
            val resp = ApiClient.get("/api/analysis/$ticketId/cascade/status")
            if (ApiClient.handleUnauthorized(resp)) return false
            resp.bodyAsText().contains("\"RUNNING\"")
        } catch (_: Exception) { false }
    }

    private val allBtnIds = listOf("btn-generate-brd", "btn-generate-fsd", "btn-generate-slides", "btn-generate-all")

    private fun applyCascadeLock() {
        disableAllButtons()
        val tip = "Đang phân tích linked tickets — vui lòng đợi cascade analysis hoàn tất"
        allBtnIds.forEach { (document.getElementById(it) as? HTMLElement)?.title = tip }
    }

    private fun removeCascadeLock() {
        cascadeUnlockJob?.cancel(); cascadeUnlockJob = null; enableAllButtons()
        allBtnIds.forEach { (document.getElementById(it) as? HTMLElement)?.title = "" }
        updateSlidesButtonState()
    }

    private fun startCascadeUnlockPolling(ticketId: String) {
        cascadeUnlockJob?.cancel()
        cascadeUnlockJob = scope.launch {
            delay(2000)
            while (isActive) {
                if (!checkCascadeRunning(ticketId)) { removeCascadeLock(); break }
                delay(3000)
            }
        }
    }

    private suspend fun fetchActiveJobs(ticketId: String) {
        try {
            val resp = ApiClient.get("/api/analysis/$ticketId/active-jobs")
            if (ApiClient.handleUnauthorized(resp)) return
            activeJobs = json.decodeFromString(resp.bodyAsText())
            DocGenBadgeRenderer.renderInlineProgress(activeJobs)
        } catch (_: Exception) { activeJobs = emptyList() }
    }

    private suspend fun fetchExistingDocuments(ticketId: String) {
        try {
            val resp = ApiClient.get("/api/analysis/$ticketId/documents")
            if (ApiClient.handleUnauthorized(resp)) return
            existingDocs = json.decodeFromString(resp.bodyAsText())
            DocGenBadgeRenderer.renderBadges(existingDocs) { openExistingDocument(it) }
            updateSlidesButtonState()
        } catch (_: Exception) { existingDocs = emptyList() }
    }

    private fun applyGenerationLock() {
        val hasActive = activeJobs.any { it.status in listOf("QUEUED", "RUNNING") }
        val allBtn = document.getElementById("btn-generate-all") as? HTMLElement
        if (hasActive) {
            lockIfActive("btn-generate-brd", "BRD")
            lockIfActive("btn-generate-fsd", "FSD")
            lockIfActive("btn-generate-slides", "REQUIREMENT_SLIDES")
            allBtn?.let { DocGenButtonHelper.disableButton(it) }
        } else {
            unlockBtn("btn-generate-brd", "RE-GENERATE BRD", "GENERATE BRD", "BRD")
            unlockBtn("btn-generate-fsd", "RE-GENERATE FSD", "GENERATE FSD", "FSD")
            unlockBtn("btn-generate-slides", "GENERATE SLIDES", "GENERATE SLIDES", "REQUIREMENT_SLIDES")
            allBtn?.let { DocGenButtonHelper.enableButton(it) }
        }
    }

    private fun unlockBtn(btnId: String, regen: String, default: String, docType: String) {
        val btn = document.getElementById(btnId) as? HTMLElement ?: return
        btn.textContent = if (existingDocs.any { it.documentType == docType }) regen else default
        DocGenButtonHelper.enableButton(btn)
    }

    private fun lockIfActive(btnId: String, docType: String) {
        val btn = document.getElementById(btnId) as? HTMLElement ?: return
        if (activeJobs.any { it.documentType == docType && it.status in listOf("QUEUED", "RUNNING") }) {
            DocGenButtonHelper.disableButton(btn); btn.textContent = "Đang sinh..."
        }
    }

    private fun applyDependencyTooltips() {
        val hasBrd = existingDocs.any { it.documentType == "BRD" && it.approvalStatus in listOf("DRAFT", "APPROVED") }
        (document.getElementById("btn-generate-fsd") as? HTMLElement)?.title = if (!hasBrd) "Cần sinh BRD trước" else ""
        (document.getElementById("btn-generate-slides") as? HTMLElement)?.title = if (!hasBrd) "Cần sinh BRD trước" else ""
    }

    private fun updateSlidesButtonState() {
        val btn = document.getElementById("btn-generate-slides") as? HTMLElement ?: return
        if (existingDocs.any { it.documentType == "BRD" }) DocGenButtonHelper.enableButton(btn)
        else DocGenButtonHelper.disableButton(btn)
    }

    private fun applyRBAC() { if (!ApiClient.hasPermission(Permission.ANALYZE_AI)) disableAllButtons() }

    private fun disableAllButtons() = allBtnIds.forEach {
        (document.getElementById(it) as? HTMLElement)?.let { b -> DocGenButtonHelper.disableButton(b) }
    }

    private fun enableAllButtons() = listOf("btn-generate-brd", "btn-generate-fsd", "btn-generate-all").forEach {
        (document.getElementById(it) as? HTMLElement)?.let { b -> DocGenButtonHelper.enableButton(b) }
    }

    private fun bindButtons(ticketId: String) {
        bindButton("btn-generate-brd", ticketId, "BRD")
        bindButton("btn-generate-fsd", ticketId, "FSD")
        bindButton("btn-generate-slides", ticketId, "REQUIREMENT_SLIDES")
        bindGenerateAllButton(ticketId)
        bindCancelButtons(ticketId)
    }

    private fun bindButton(btnId: String, ticketId: String, docType: String) {
        val btn = document.getElementById(btnId) as? HTMLElement ?: return
        val clone = btn.cloneNode(true) as HTMLElement
        btn.parentNode?.replaceChild(clone, btn)
        clone.addEventListener("click", {
            if (clone.getAttribute("disabled") != null) return@addEventListener
            DocumentGenerationFlow.startGeneration(ticketId, docType)
        })
    }

    private fun bindGenerateAllButton(ticketId: String) {
        val btn = document.getElementById("btn-generate-all") as? HTMLElement ?: return
        val clone = btn.cloneNode(true) as HTMLElement
        btn.parentNode?.replaceChild(clone, btn)
        clone.addEventListener("click", {
            if (clone.getAttribute("disabled") != null) return@addEventListener
            DocumentGenerationFlow.startGenerateAll(ticketId)
        })
    }

    private fun bindCancelButtons(ticketId: String) {
        for (job in activeJobs) {
            val areaId = DocGenButtonHelper.progressAreaId(job.documentType)
            val area = document.getElementById(areaId) as? HTMLElement ?: continue
            val cancelBtn = area.querySelector(".btn-cancel-job") as? HTMLElement ?: continue
            cancelBtn.addEventListener("click", {
                cancelBtn.setAttribute("disabled", "true")
                DocumentGenerationFlow.cancelJob(job.jobId, ticketId, job.documentType)
                window.setTimeout({ cancelBtn.removeAttribute("disabled") }, 2000)
            })
        }
    }

    private fun openExistingDocument(docType: String) {
        val ticketId = TicketCombobox.selectedTicket?.ticketId ?: return
        DocumentGenerationFlow.fetchAndPreview(ticketId, docType)
    }

    private fun getSection(): HTMLElement? =
        document.getElementById("ti-docgen-section") as? HTMLElement
}
