package com.assistant.frontend.pages

import com.assistant.auth.UserRole
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.pages.settings.SettingsSaveHandler
import com.assistant.frontend.pages.settings.SettingsCurationToggle
import com.assistant.frontend.pages.settings.SettingsAgentPipelineToggle
import com.assistant.frontend.services.ValidationService
import com.assistant.settings.AppSettingsResponse
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Settings page — Application configuration management (MH8).
 * Requirements: 8.1, 8.2, 11.1, 16.1, 17.1
 */
object SettingsPage {

    internal val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(container: Element) {
        container.innerHTML = ""
        scope.launch {
            val html = ApiClient.loadTemplate("settings")
            container.innerHTML = html
            val role = ApiClient.getUserRole()
            if (role != UserRole.ADMINISTRATOR) {
                showAccessDenied()
                return@launch
            }
            showMainContent()
            bindEvents()
            loadSettings()
            SettingsCurationToggle.init()
            SettingsAgentPipelineToggle.init()
        }
    }

    private fun showAccessDenied() {
        (document.getElementById("settings-access-denied") as? HTMLElement)
            ?.style?.display = ""
        (document.getElementById("settings-main-content") as? HTMLElement)
            ?.style?.display = "none"
    }

    private fun showMainContent() {
        (document.getElementById("settings-access-denied") as? HTMLElement)
            ?.style?.display = "none"
        (document.getElementById("settings-main-content") as? HTMLElement)
            ?.style?.display = ""
    }

    private fun bindEvents() {
        document.getElementById("btn-save-settings")?.addEventListener("click", {
            scope.launch { SettingsSaveHandler.save(json, ::loadSettings) }
        })
        bindToggleVisibility()
    }

    private fun bindToggleVisibility() {
        val toggleButtons = document.querySelectorAll(".btn-toggle-visibility")
        for (i in 0 until toggleButtons.length) {
            val btn = toggleButtons.item(i) as? HTMLElement ?: continue
            btn.addEventListener("click", {
                val targetId = btn.getAttribute("data-target")
                    ?: return@addEventListener
                val input = document.getElementById(targetId)
                    as? HTMLInputElement ?: return@addEventListener
                if (input.type == "password") {
                    input.type = "text"; btn.textContent = "🙈"
                } else {
                    input.type = "password"; btn.textContent = "👁"
                }
            })
        }
    }

    internal suspend fun loadSettings() {
        try {
            val response = ApiClient.get("/api/settings")
            if (ApiClient.handleUnauthorized(response)) return
            val body = response.bodyAsText()
            val settings = json.decodeFromString<AppSettingsResponse>(body)
            bindSettingsToForm(settings)
        } catch (e: Exception) {
            console.log("[SettingsPage] Failed to load: ${e.message}")
            SettingsSaveHandler.showStatus(
                "Failed to load settings: ${e.message}", isError = true
            )
        }
    }

    private fun bindSettingsToForm(settings: AppSettingsResponse) {
        setInput("input-jira-host", settings.jiraHost ?: "")
        setInput("input-ai-provider-url", settings.aiProviderUrl ?: "")
        setMaskedInput("input-jwt-secret", settings.jwtSecret)
        setMaskedInput("input-encryption-key", settings.encryptionKey)
        setReadonlyInput("input-port", settings.port?.toString() ?: "")
    }

    private fun setInput(id: String, value: String) {
        (document.getElementById(id) as? HTMLInputElement)?.value = value
    }

    private fun setMaskedInput(id: String, value: String?) {
        (document.getElementById(id) as? HTMLInputElement)?.let {
            it.value = value?.replace("*", "•") ?: ""
            it.placeholder = "••••••••••••"
        }
    }

    private fun setReadonlyInput(id: String, value: String) {
        (document.getElementById(id) as? HTMLInputElement)?.let {
            it.value = value; it.disabled = true
        }
    }
}
