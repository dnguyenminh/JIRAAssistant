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
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Jira-specific configuration modal: domain, email, API token.
 */
internal object IntegrationsJiraModal {

    fun openJiraConfigModal(provider: ProviderInfo) {
        val modal = document.getElementById("jira-config-modal") as? HTMLElement ?: return
        modal.style.display = "flex"
        val domainInput = document.getElementById("jira-domain") as? HTMLInputElement
        if (!provider.endpoint.isNullOrBlank()) domainInput?.value = provider.endpoint
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
        document.getElementById("btn-jira-save-test")?.addEventListener("click", {
            if (ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)) saveAndTestJiraConfig(provider)
        })
    }

    private fun saveAndTestJiraConfig(provider: ProviderInfo) {
        val btn = document.getElementById("btn-jira-save-test") as? HTMLElement ?: return
        val progressEl = document.getElementById("jira-config-progress") as? HTMLElement ?: return
        val bar = document.getElementById("jira-config-progress-bar") as? HTMLElement ?: return
        val statusEl = document.getElementById("jira-config-status") as? HTMLElement ?: return

        val domain = (document.getElementById("jira-domain") as? HTMLInputElement)?.value?.trim() ?: ""
        val email = (document.getElementById("jira-email") as? HTMLInputElement)?.value?.trim() ?: ""
        val apiToken = (document.getElementById("jira-api-token") as? HTMLInputElement)?.value?.trim() ?: ""

        if (domain.isBlank() || email.isBlank() || apiToken.isBlank()) {
            showJiraStatus(statusEl, "All fields are required", true); return
        }

        btn.textContent = "SAVING..."; btn.style.opacity = "0.6"; btn.style.asDynamic().pointerEvents = "none"
        progressEl.style.display = "block"; bar.style.width = "0%"; statusEl.style.display = "none"
        var progress = 0
        val intervalId = window.setInterval({ progress += 4; bar.style.width = "${minOf(progress, 85)}%" }, 60)

        BlockingOverlay.show("jira-modal-content", "Saving & testing...")
        IntegrationsPage.scope.launch {
            try {
                val configRequest = JiraConfigRequest(domain = domain, email = email, apiToken = apiToken)
                val response = ApiClient.put("/api/integrations/jira/config", configRequest)
                if (ApiClient.handleUnauthorized(response)) return@launch
                window.clearInterval(intervalId); bar.style.width = "100%"
                val body = response.bodyAsText()
                val result = IntegrationsPage.json.decodeFromString<JiraConfigResponse>(body)
                handleJiraResult(provider, result, statusEl)
            } catch (e: Exception) {
                window.clearInterval(intervalId); bar.style.width = "100%"
                showJiraStatus(statusEl, "Error: ${e.message ?: "Save failed"}", true)
                ToastService.show("Failed to save Jira configuration", "error")
            } finally {
                btn.textContent = "SAVE & TEST"; btn.style.opacity = "1"; btn.style.asDynamic().pointerEvents = "auto"
                BlockingOverlay.remove("jira-modal-content")
            }
        }
    }

    private suspend fun handleJiraResult(provider: ProviderInfo, result: JiraConfigResponse, statusEl: HTMLElement) {
        if (result.status.uppercase() == "ACTIVE") {
            IntegrationsPage.updateCardStatus(provider.providerId, "ACTIVE", null)
            val idx = IntegrationsPage.providers.indexOfFirst { it.providerId == provider.providerId }
            if (idx >= 0) IntegrationsPage.providers[idx] = IntegrationsPage.providers[idx].copy(status = "ACTIVE", endpoint = (document.getElementById("jira-domain") as? HTMLInputElement)?.value?.trim())
            showJiraStatus(statusEl, "Configuration saved ✓ Connected to Jira", false)
            ToastService.show("Jira configuration saved successfully", "success")
            delay(1500); closeJiraConfigModal(); IntegrationsPage.renderProviderCards()
        } else {
            val errorMsg = result.error ?: "Connection failed"
            IntegrationsPage.updateCardStatus(provider.providerId, "OFFLINE", null)
            val idx = IntegrationsPage.providers.indexOfFirst { it.providerId == provider.providerId }
            if (idx >= 0) IntegrationsPage.providers[idx] = IntegrationsPage.providers[idx].copy(status = "OFFLINE")
            showJiraStatus(statusEl, "Error: $errorMsg", true)
            ToastService.show("Jira connection failed: $errorMsg", "error")
        }
    }

    private fun showJiraStatus(statusEl: HTMLElement, message: String, isError: Boolean) {
        statusEl.textContent = message; statusEl.style.display = "block"
        statusEl.style.color = if (isError) "var(--danger)" else "var(--primary)"
        statusEl.style.background = if (isError) "rgba(255,59,48,0.1)" else "rgba(45,254,207,0.1)"
    }
}
