package com.assistant.frontend.pages.settings

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.services.ValidationService
import com.assistant.settings.AppSettings
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Handles settings save flow: validation, progress bar, API call.
 */
internal object SettingsSaveHandler {

    suspend fun save(json: Json, reloadFn: suspend () -> Unit) {
        val values = gatherFormValues()
        val errors = validate(values)
        if (errors.isNotEmpty()) {
            showStatus(errors.joinToString(". "), isError = true)
            return
        }
        val settings = buildPayload(values)
        executeSave(settings, reloadFn)
    }

    private data class FormValues(
        val jiraHost: String,
        val aiProviderUrl: String,
        val jwtSecret: String,
        val encryptionKey: String
    )

    private fun gatherFormValues(): FormValues = FormValues(
        jiraHost = inputVal("input-jira-host"),
        aiProviderUrl = inputVal("input-ai-provider-url"),
        jwtSecret = inputVal("input-jwt-secret"),
        encryptionKey = inputVal("input-encryption-key")
    )

    private fun validate(v: FormValues): List<String> {
        val errors = mutableListOf<String>()
        if (v.jiraHost.isNotBlank() && !ValidationService.isValidUrl(v.jiraHost))
            errors.add("JIRA_HOST must be a valid URL")
        if (v.aiProviderUrl.isNotBlank() && !ValidationService.isValidUrl(v.aiProviderUrl))
            errors.add("AI_PROVIDER_URL must be a valid URL")
        return errors
    }

    private fun buildPayload(v: FormValues) = AppSettings(
        jiraHost = v.jiraHost.ifBlank { null },
        aiProviderUrl = v.aiProviderUrl.ifBlank { null },
        jwtSecret = cleanSecret(v.jwtSecret),
        encryptionKey = cleanSecret(v.encryptionKey)
    )

    private fun cleanSecret(value: String): String? =
        if (value.isNotBlank() && !value.contains("•")) value else null

    private suspend fun executeSave(
        settings: AppSettings, reloadFn: suspend () -> Unit
    ) {
        val btn = document.getElementById("btn-save-settings") as? HTMLButtonElement
        val originalText = btn?.textContent ?: "SAVE SETTINGS"
        btn?.textContent = "SAVING..."; btn?.disabled = true

        val progressEl = el("settings-progress")
        val barEl = el("settings-progress-bar")
        progressEl?.style?.display = "block"
        barEl?.style?.width = "0%"
        hideStatus()

        var progress = 0
        val intervalId = window.setInterval({
            progress += 6
            barEl?.style?.width = "${minOf(progress, 90)}%"
        }, 80)

        try {
            val response = ApiClient.put("/api/settings", settings)
            if (ApiClient.handleUnauthorized(response)) return
            window.clearInterval(intervalId)
            barEl?.style?.width = "100%"
            if (response.status.isSuccess()) {
                delay(300)
                showStatus("✓ Settings saved successfully", isError = false)
                reloadFn()
            } else {
                showStatus("Save failed: ${response.bodyAsText()}", isError = true)
            }
        } catch (e: Exception) {
            window.clearInterval(intervalId)
            barEl?.style?.width = "100%"
            showStatus("Save failed: ${e.message}", isError = true)
        } finally {
            delay(400)
            progressEl?.style?.display = "none"
            barEl?.style?.width = "0%"
            btn?.textContent = originalText; btn?.disabled = false
        }
    }

    fun showStatus(message: String, isError: Boolean) {
        val msgEl = el("settings-status-msg") ?: return
        msgEl.style.display = "block"
        msgEl.textContent = message
        msgEl.style.color = if (isError) "var(--danger)" else "var(--primary)"
    }

    fun hideStatus() {
        val msgEl = el("settings-status-msg") ?: return
        msgEl.style.display = "none"; msgEl.textContent = ""
    }

    private fun inputVal(id: String): String =
        (document.getElementById(id) as? HTMLInputElement)?.value?.trim() ?: ""

    private fun el(id: String): HTMLElement? =
        document.getElementById(id) as? HTMLElement
}
