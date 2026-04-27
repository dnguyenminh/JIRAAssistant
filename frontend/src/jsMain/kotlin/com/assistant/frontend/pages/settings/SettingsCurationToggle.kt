package com.assistant.frontend.pages.settings

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.pages.SettingsPage
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Prompt Curation toggle for the Settings page.
 * Manages the prompt_curation_enabled feature flag
 * via the /api/settings/feature endpoint.
 */
object SettingsCurationToggle {

    private val json = Json { ignoreUnknownKeys = true }
    private const val FEATURE_KEY = "prompt_curation_enabled"

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
            console.log("[Curation] Load failed: ${e.message}")
            setChecked(false)
        }
    }

    private fun bindToggleEvent() {
        val checkbox = document.getElementById(
            "toggle-prompt-curation"
        ) as? HTMLInputElement ?: return
        checkbox.addEventListener("change", {
            val enabled = checkbox.checked
            SettingsPage.scope.launch { saveState(enabled) }
        })
    }

    private suspend fun saveState(enabled: Boolean) {
        val msg = document.getElementById(
            "curation-status-msg"
        ) as? HTMLElement
        try {
            val value = if (enabled) "true" else "false"
            ApiClient.put(
                "/api/settings/feature",
                CurationToggleBody(FEATURE_KEY, value)
            )
            updateSlider(enabled)
            showStatus(msg, enabled)
        } catch (e: Exception) {
            console.log("[Curation] Save failed: ${e.message}")
            setChecked(!enabled)
            showError(msg, e.message)
        }
    }

    private fun setChecked(checked: Boolean) {
        val cb = document.getElementById(
            "toggle-prompt-curation"
        ) as? HTMLInputElement ?: return
        cb.checked = checked
        updateSlider(checked)
    }

    private fun updateSlider(enabled: Boolean) {
        val slider = document.getElementById(
            "toggle-prompt-curation-slider"
        ) as? HTMLElement ?: return
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
            "\u2713 Prompt curation enabled \u2014 prompts will be 50K\u201380K instead of 200K+"
        else
            "\u2713 Prompt curation disabled \u2014 using raw data in prompts"
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

@Serializable
data class CurationToggleResp(
    val key: String,
    val value: String
)

@Serializable
data class CurationToggleBody(
    val key: String,
    val value: String
)
