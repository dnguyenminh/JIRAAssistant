package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.GenerationJobDto
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Renders inline progress UI for document generation jobs.
 * Handles progress bar, elapsed timer, cancel button, error panel, and animations.
 * Requirements: 1.1, 2.2, 2.3, 3.1, 3.2, 4.1, 5.1, 5.3, 6.1-6.4
 */
internal object InlineProgressRenderer {

    private const val TIMEOUT_THRESHOLD_SECONDS = 240

    /**
     * Registers a one-time event delegation listener on the progress area.
     * This survives innerHTML rebuilds since the listener is on the parent.
     */
    fun installCancelDelegate(areaId: String, onCancel: () -> Unit) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        // Mark to avoid duplicate listeners
        if (area.getAttribute("data-cancel-bound") == "1") return
        area.setAttribute("data-cancel-bound", "1")
        area.addEventListener("click", { evt ->
            val target = (evt.target as? HTMLElement) ?: return@addEventListener
            if (target.classList.contains("btn-cancel-job")) {
                onCancel()
            }
        })
    }

    /** Remove the cancel delegate marker so a new one can be installed. */
    fun removeCancelDelegate(areaId: String) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        area.removeAttribute("data-cancel-bound")
    }

    fun renderProgress(areaId: String, job: GenerationJobDto) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        area.style.display = "block"
        area.innerHTML = ""
        val tmpl = document.getElementById("tmpl-inline-progress") as? HTMLTemplateElement ?: return
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return
        populateProgressBar(el, job)
        populatePhaseLabel(el, job)
        populateElapsed(el, job)
        populateTimeoutWarning(el, job)
        area.appendChild(el)
    }

    fun renderError(areaId: String, job: GenerationJobDto, onRetry: () -> Unit) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        area.style.display = "block"
        area.innerHTML = ""
        val tmpl = document.getElementById("tmpl-error-panel") as? HTMLTemplateElement ?: return
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return
        populateErrorPanel(el, job, onRetry)
        area.appendChild(el)
        window.setTimeout({ area.innerHTML = "" }, 30000)
    }

    fun renderSuccess(areaId: String, onComplete: () -> Unit) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        area.style.display = "block"
        area.innerHTML = ""
        val tmpl = document.getElementById("tmpl-inline-progress") as? HTMLTemplateElement ?: return
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return
        val fill = el.querySelector(".inline-progress-bar-fill") as? HTMLElement
        fill?.style?.width = "100%"
        fill?.classList?.add("success")
        hideElement(el, ".inline-progress-actions")
        hideElement(el, ".inline-progress-info")
        area.appendChild(el)
        window.setTimeout({ clearProgress(areaId); onComplete() }, 1500)
    }

    fun startElapsedTimer(areaId: String, startedAt: String): Int {
        return window.setInterval({
            val area = document.getElementById(areaId) as? HTMLElement ?: return@setInterval
            val elapsedEl = area.querySelector(".inline-progress-elapsed") as? HTMLElement
                ?: return@setInterval
            val elapsed = computeElapsedSeconds(startedAt)
            elapsedEl.textContent = formatElapsed(elapsed)
        }, 1000)
    }

    fun stopElapsedTimer(intervalId: Int) {
        window.clearInterval(intervalId)
    }

    fun clearProgress(areaId: String) {
        val area = document.getElementById(areaId) as? HTMLElement ?: return
        area.innerHTML = ""
        area.style.display = "none"
    }

    // --- Pure functions for testing ---

    fun formatElapsed(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "${m}m ${s}s"
    }

    fun shouldShowTimeoutWarning(elapsedSeconds: Int): Boolean {
        return elapsedSeconds > TIMEOUT_THRESHOLD_SECONDS
    }

    // --- Private helpers ---

    private fun populateProgressBar(el: HTMLElement, job: GenerationJobDto) {
        val fill = el.querySelector(".inline-progress-bar-fill") as? HTMLElement ?: return
        fill.style.width = "${job.progressPercent}%"
        if (job.phase == "GENERATING_DOCUMENT") fill.classList.add("shimmer")
        if (job.status == "FAILED") fill.classList.add("failed")
    }

    private fun populatePhaseLabel(el: HTMLElement, job: GenerationJobDto) {
        val label = el.querySelector(".inline-progress-phase-label") as? HTMLElement ?: return
        label.textContent = job.phaseLabel ?: "${job.phase} ${job.progressPercent}%"
    }

    private fun populateElapsed(el: HTMLElement, job: GenerationJobDto) {
        val elapsedEl = el.querySelector(".inline-progress-elapsed") as? HTMLElement ?: return
        if (job.startedAt != null) {
            val elapsed = computeElapsedSeconds(job.startedAt)
            elapsedEl.textContent = formatElapsed(elapsed)
        } else {
            elapsedEl.textContent = "\u2014"
        }
    }

    private fun populateTimeoutWarning(el: HTMLElement, job: GenerationJobDto) {
        if (job.startedAt == null) return
        val elapsed = computeElapsedSeconds(job.startedAt)
        if (shouldShowTimeoutWarning(elapsed)) {
            val warning = el.querySelector(".inline-progress-timeout-warning") as? HTMLElement
            warning?.style?.display = "block"
        }
    }

    private fun populateErrorPanel(
        el: HTMLElement, job: GenerationJobDto, onRetry: () -> Unit
    ) {
        val msg = el.querySelector(".error-panel-message") as? HTMLElement
        msg?.textContent = job.errorMessage ?: "Generation failed"
        val retryBtn = el.querySelector(".btn-error-retry") as? HTMLElement
        retryBtn?.addEventListener("click", { onRetry() })
        val closeBtn = el.querySelector(".btn-error-close") as? HTMLElement
        closeBtn?.addEventListener("click", {
            el.parentElement?.let { p ->
                p.innerHTML = ""
                (p as? HTMLElement)?.style?.display = "none"
            }
        })
        if (job.errorMessage?.contains("provider", ignoreCase = true) == true) {
            val link = el.querySelector(".error-panel-link") as? HTMLElement
            link?.style?.display = "inline"
            link?.textContent = "Kiểm tra Integrations"
            link?.setAttribute("href", "#integrations")
        }
    }

    private fun hideElement(parent: HTMLElement, selector: String) {
        val child = parent.querySelector(selector) as? HTMLElement
        child?.style?.display = "none"
    }

    private fun computeElapsedSeconds(startedAt: String): Int {
        return try {
            val startMs = js("new Date(startedAt).getTime()") as Double
            val nowMs = js("Date.now()") as Double
            ((nowMs - startMs) / 1000).toInt().coerceAtLeast(0)
        } catch (_: dynamic) {
            0
        }
    }
}
