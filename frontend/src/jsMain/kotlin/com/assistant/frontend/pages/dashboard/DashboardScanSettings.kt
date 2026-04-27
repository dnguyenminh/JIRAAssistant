package com.assistant.frontend.pages.dashboard

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement

/**
 * Persists scan control settings (concurrency, batch size, force) in sessionStorage.
 * Restores values when Dashboard re-renders after navigation.
 */
internal object DashboardScanSettings {

    private const val KEY_CONCURRENCY = "scan_concurrency"
    private const val KEY_BATCH_SIZE = "scan_batch_prompt_size"
    private const val KEY_FORCE = "scan_force_reanalyze"

    fun restore() {
        val storage = window.sessionStorage
        storage.getItem(KEY_CONCURRENCY)?.let { inputEl("scan-concurrency")?.value = it }
        storage.getItem(KEY_BATCH_SIZE)?.let { inputEl("scan-batch-prompt-size")?.value = it }
        storage.getItem(KEY_FORCE)?.let { inputEl("scan-force-reanalyze")?.checked = it == "true" }
    }

    fun bindPersistence(getConcurrency: () -> Int, getBatchSize: () -> Int, getForce: () -> Boolean) {
        val storage = window.sessionStorage
        inputEl("scan-concurrency")?.addEventListener("change", {
            storage.setItem(KEY_CONCURRENCY, getConcurrency().toString())
        })
        inputEl("scan-batch-prompt-size")?.addEventListener("change", {
            storage.setItem(KEY_BATCH_SIZE, getBatchSize().toString())
        })
        inputEl("scan-force-reanalyze")?.addEventListener("change", {
            storage.setItem(KEY_FORCE, getForce().toString())
        })
    }

    private fun inputEl(id: String): HTMLInputElement? =
        document.getElementById(id) as? HTMLInputElement
}
