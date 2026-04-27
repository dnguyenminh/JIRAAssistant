package com.assistant.frontend.pages.settings

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.pages.SettingsPage
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Agent Pipeline toggle for the Settings page.
 * Manages the agent_pipeline_enabled feature flag
 * via the /api/settings/feature endpoint.
 */
object SettingsAgentPipelineToggle {

    private val json = Json { ignoreUnknownKeys = true }
    private const val FEATURE_KEY = "agent_pipeline_enabled"
    private const val CHECKBOX_ID = "toggle-agent-pipeline-settings"
    private const val SLIDER_ID = "toggle-agent-pipeline-settings-slider"
    private const val MSG_ID = "agent-pipeline-settings-msg"

    fun init() {
        SettingsPage.scope.launch { loadState() }
        bindToggleEvent()
    }

    private suspend fun loadState() {
        try {
            val resp = ApiClient.get(
                "/api/settings/feature?key=$FEATURE_KEY"
            )
            val body = resp.bodyAsText()
            val toggle = json.decodeFromString<CurationToggleResp>(body)
            setChecked(toggle.value == "true")
        } catch (e: Exception) {
            console.log("[AgentPipeline] Load failed: ${e.message}")
            setChecked(false)
        }
    }

    private fun bindToggleEvent() {
        val cb = document.getElementById(CHECKBOX_ID)
            as? HTMLInputElement ?: return
        cb.addEventListener("change", {
            val enabled = cb.checked
            SettingsPage.scope.launch { saveState(enabled) }
        })
    }

    private suspend fun saveState(enabled: Boolean) {
        val msg = document.getElementById(MSG_ID) as? HTMLElement
        try {
            val value = if (enabled) "true" else "false"
            ApiClient.put(
                "/api/settings/feature",
                CurationToggleBody(FEATURE_KEY, value)
            )
            updateSlider(enabled)
            showStatus(msg, enabled)
        } catch (e: Exception) {
            console.log("[AgentPipeline] Save failed: ${e.message}")
            setChecked(!enabled)
            showError(msg, e.message)
        }
    }

    private fun setChecked(checked: Boolean) {
        val cb = document.getElementById(CHECKBOX_ID)
            as? HTMLInputElement ?: return
        cb.checked = checked
        updateSlider(checked)
    }

    private fun updateSlider(enabled: Boolean) {
        val slider = document.getElementById(SLIDER_ID)
            as? HTMLElement ?: return
        if (enabled) {
            slider.style.background = "var(--primary)"
            slider.style.cssText += "box-shadow:0 0 8px var(--primary);"
        } else {
            slider.style.background = "rgba(255,255,255,0.1)"
            slider.style.cssText = slider.style.cssText
                .replace(Regex("box-shadow:[^;]+;"), "")
        }
    }

    private fun showStatus(el: HTMLElement?, enabled: Boolean) {
        el ?: return
        el.style.display = ""
        el.style.color = "var(--primary)"
        el.style.background = "rgba(0,255,136,0.05)"
        el.textContent = if (enabled)
            "\u2713 Agent pipeline enabled \u2014 prompts 60K\u201370K"
        else
            "\u2713 Agent pipeline disabled \u2014 using legacy pipeline"
        kotlinx.browser.window.setTimeout({
            el.style.display = "none"
        }, 3000)
    }

    private fun showError(el: HTMLElement?, msg: String?) {
        el ?: return
        el.style.display = ""
        el.style.color = "var(--danger)"
        el.style.background = "rgba(255,0,0,0.05)"
        el.textContent = "\u2717 Failed to save: ${msg ?: "unknown"}"
    }
}
