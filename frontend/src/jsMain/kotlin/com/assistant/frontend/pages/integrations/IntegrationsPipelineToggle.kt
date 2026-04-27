package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.pages.IntegrationsPage
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Pipeline toggle logic for the Integrations page.
 * Manages the agent_pipeline_enabled feature flag via
 * the /api/settings/feature endpoint.
 */

private val json = Json { ignoreUnknownKeys = true }
private const val FEATURE_KEY = "agent_pipeline_enabled"

@Serializable
data class FeatureToggleResp(
    val key: String,
    val value: String
)

fun IntegrationsPage.initPipelineToggle() {
    scope.launch { loadToggleState() }
    bindToggleEvent()
}

private suspend fun loadToggleState() {
    try {
        val resp = ApiClient.get(
            "/api/settings/feature?key=$FEATURE_KEY"
        )
        val body = resp.bodyAsText()
        val toggle = json.decodeFromString<FeatureToggleResp>(body)
        setToggleChecked(toggle.value == "true")
    } catch (e: Exception) {
        console.log("[Pipeline] Load failed: ${e.message}")
        setToggleChecked(false)
    }
}

private fun bindToggleEvent() {
    val checkbox = document.getElementById(
        "toggle-agent-pipeline"
    ) as? HTMLInputElement ?: return
    checkbox.addEventListener("change", {
        val enabled = checkbox.checked
        IntegrationsPage.scope.launch {
            saveToggleState(enabled)
        }
    })
}

private suspend fun saveToggleState(enabled: Boolean) {
    val msg = document.getElementById(
        "pipeline-status-msg"
    ) as? HTMLElement
    try {
        val value = if (enabled) "true" else "false"
        ApiClient.put(
            "/api/settings/feature",
            FeatureToggleBody(FEATURE_KEY, value)
        )
        updateSliderStyle(enabled)
        showStatusMsg(msg, enabled)
    } catch (e: Exception) {
        console.log("[Pipeline] Save failed: ${e.message}")
        setToggleChecked(!enabled)
        showErrorMsg(msg, e.message)
    }
}

private fun setToggleChecked(checked: Boolean) {
    val cb = document.getElementById(
        "toggle-agent-pipeline"
    ) as? HTMLInputElement ?: return
    cb.checked = checked
    updateSliderStyle(checked)
}

private fun updateSliderStyle(enabled: Boolean) {
    val slider = document.getElementById(
        "toggle-agent-pipeline-slider"
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

private fun showStatusMsg(
    el: HTMLElement?, enabled: Boolean
) {
    el ?: return
    el.style.display = ""
    el.style.color = "var(--primary)"
    el.style.background = "rgba(0,255,136,0.05)"
    el.textContent = if (enabled)
        "✓ Agent pipeline enabled — prompts will be 60K-70K instead of 200K+"
    else
        "✓ Agent pipeline disabled — using legacy pipeline"
    kotlinx.browser.window.setTimeout({
        el.style.display = "none"
    }, 3000)
}

private fun showErrorMsg(el: HTMLElement?, msg: String?) {
    el ?: return
    el.style.display = ""
    el.style.color = "var(--danger)"
    el.style.background = "rgba(255,0,0,0.05)"
    el.textContent = "✗ Failed to save: ${msg ?: "unknown"}"
}

@Serializable
data class FeatureToggleBody(
    val key: String,
    val value: String
)
