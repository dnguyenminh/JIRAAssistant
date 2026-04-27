package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.JiraConfigRequest
import com.assistant.frontend.models.JiraConfigResponse
import com.assistant.frontend.models.ProviderInfo
import com.assistant.frontend.pages.IntegrationsPage
import com.assistant.frontend.services.ToastService
import com.assistant.rbac.Permission
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Jira-specific configuration modal: domain, email, API token.
 * Two-step flow: TEST CONNECTION → SAVE (matching AI provider modal pattern).
 */
internal object IntegrationsJiraModal {

    fun openJiraConfigModal(provider: ProviderInfo) {
        val modal = document.getElementById("jira-config-modal") as? HTMLElement ?: return
        modal.style.display = "flex"
        val domainInput = document.getElementById("jira-domain") as? HTMLInputElement
        if (!provider.endpoint.isNullOrBlank()) domainInput?.value = provider.endpoint
        resetSaveButton()
        bindJiraModalEvents(provider)
    }

    private fun closeJiraConfigModal() {
        val modal = document.getElementById("jira-config-modal") as? HTMLElement ?: return
        modal.style.display = "none"
        (document.getElementById("jira-config-progress") as? HTMLElement)?.style?.display = "none"
        (document.getElementById("jira-config-status") as? HTMLElement)?.style?.display = "none"
    }

    private fun bindJiraModalEvents(provider: ProviderInfo) {
        document.getElementById("jira-modal-close")?.addEventListener("click", { closeJiraConfigModal() })
        val modal = document.getElementById("jira-config-modal") as? HTMLElement
        modal?.addEventListener("click", { e -> if (e.target == modal) closeJiraConfigModal() })
        document.getElementById("jira-toggle-token")?.addEventListener("click", {
            val tokenInput = document.getElementById("jira-api-token") as? HTMLInputElement ?: return@addEventListener
            tokenInput.type = if (tokenInput.type == "password") "text" else "password"
        })
        document.getElementById("btn-jira-test")?.addEventListener("click", {
            if (ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)) testJiraConnection(provider)
        })
        document.getElementById("btn-jira-save")?.addEventListener("click", {
            if (ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)) saveJiraConfig(provider)
        })
    }

    private fun readFormValues(): Triple<String, String, String> {
        val domain = (document.getElementById("jira-domain") as? HTMLInputElement)?.value?.trim() ?: ""
        val email = (document.getElementById("jira-email") as? HTMLInputElement)?.value?.trim() ?: ""
        val apiToken = (document.getElementById("jira-api-token") as? HTMLInputElement)?.value?.trim() ?: ""
        return Triple(domain, email, apiToken)
    }

    private fun testJiraConnection(provider: ProviderInfo) {
        val testBtn = document.getElementById("btn-jira-test") as? HTMLElement ?: return
        val statusEl = document.getElementById("jira-config-status") as? HTMLElement ?: return
        val progressEl = document.getElementById("jira-config-progress") as? HTMLElement ?: return
        val bar = document.getElementById("jira-config-progress-bar") as? HTMLElement ?: return

        val (domain, email, apiToken) = readFormValues()
        if (domain.isBlank() || email.isBlank() || apiToken.isBlank()) {
            showJiraStatus(statusEl, "All fields are required", true); return
        }

        testBtn.textContent = "TESTING..."; testBtn.style.opacity = "0.6"; testBtn.style.asDynamic().pointerEvents = "none"
        progressEl.style.display = "block"; bar.style.width = "0%"; statusEl.style.display = "none"
        var progress = 0
        val intervalId = window.setInterval({ progress += 5; bar.style.width = "${minOf(progress, 90)}%" }, 80)

        BlockingOverlay.show("jira-modal-content", "Testing connection...")
        IntegrationsPage.scope.launch {
            try {
                val configRequest = JiraConfigRequest(domain = domain, email = email, apiToken = apiToken)
                val response = ApiClient.put("/api/integrations/jira/config", configRequest)
                if (ApiClient.handleUnauthorized(response)) return@launch
                window.clearInterval(intervalId); bar.style.width = "100%"
                val body = response.bodyAsText()
                val result = IntegrationsPage.json.decodeFromString<JiraConfigResponse>(body)
                handleTestResult(provider, result, statusEl)
            } catch (e: Exception) {
                window.clearInterval(intervalId); bar.style.width = "100%"
                showJiraStatus(statusEl, "Error: ${e.message ?: "Connection failed"}", true)
                disableSaveButton()
            } finally {
                testBtn.textContent = "TEST CONNECTION"; testBtn.style.opacity = "1"; testBtn.style.asDynamic().pointerEvents = "auto"
                BlockingOverlay.remove("jira-modal-content")
            }
        }
    }

    private fun handleTestResult(provider: ProviderInfo, result: JiraConfigResponse, statusEl: HTMLElement) {
        if (result.status.uppercase() == "ACTIVE") {
            IntegrationsPage.updateCardStatus(provider.providerId, "ACTIVE", null)
            updateProviderState(provider, "ACTIVE")
            showJiraStatus(statusEl, "✓ Connected to Jira", false)
            enableSaveButton()
        } else {
            val errorMsg = result.error ?: "Connection failed"
            IntegrationsPage.updateCardStatus(provider.providerId, "OFFLINE", null)
            updateProviderState(provider, "OFFLINE")
            showJiraStatus(statusEl, "Error: $errorMsg", true)
            disableSaveButton()
        }
    }

    private fun saveJiraConfig(provider: ProviderInfo) {
        val statusEl = document.getElementById("jira-config-status") as? HTMLElement ?: return
        showJiraStatus(statusEl, "Configuration saved ✓", false)
        ToastService.show("Jira configuration saved successfully", "success")
        IntegrationsPage.scope.launch {
            delay(1500)
            closeJiraConfigModal()
            IntegrationsPage.renderProviderCards()
        }
    }

    private fun updateProviderState(provider: ProviderInfo, status: String) {
        val idx = IntegrationsPage.providers.indexOfFirst { it.providerId == provider.providerId }
        if (idx >= 0) {
            val domain = (document.getElementById("jira-domain") as? HTMLInputElement)?.value?.trim()
            IntegrationsPage.providers[idx] = IntegrationsPage.providers[idx].copy(status = status, endpoint = domain)
        }
    }

    private fun enableSaveButton() {
        val saveBtn = document.getElementById("btn-jira-save") as? HTMLElement ?: return
        saveBtn.style.opacity = "1"; saveBtn.style.asDynamic().pointerEvents = "auto"
        (saveBtn as? HTMLButtonElement)?.disabled = false
    }

    private fun disableSaveButton() {
        val saveBtn = document.getElementById("btn-jira-save") as? HTMLElement ?: return
        saveBtn.style.opacity = "0.4"; saveBtn.style.asDynamic().pointerEvents = "none"
        (saveBtn as? HTMLButtonElement)?.disabled = true
    }

    private fun resetSaveButton() = disableSaveButton()

    private fun showJiraStatus(statusEl: HTMLElement, message: String, isError: Boolean) {
        statusEl.textContent = message; statusEl.style.display = "block"
        statusEl.style.color = if (isError) "var(--danger)" else "var(--primary)"
        statusEl.style.background = if (isError) "rgba(255,59,48,0.1)" else "rgba(45,254,207,0.1)"
    }
}
