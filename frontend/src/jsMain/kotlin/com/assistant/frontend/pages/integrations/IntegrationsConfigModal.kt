package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.*
import com.assistant.frontend.pages.IntegrationsPage
import com.assistant.frontend.services.HtmlUtils
import com.assistant.rbac.Permission
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement

/**
 * Config modal for non-Jira providers: test connection + save.
 */
internal object IntegrationsConfigModal {

    fun openConfigModal(provider: ProviderInfo) {
        if (provider.type.uppercase() == "JIRA") {
            IntegrationsJiraModal.openJiraConfigModal(provider); return
        }
        IntegrationsPage.activeModal = provider.providerId
        val overlay = document.getElementById("integ-modal-overlay") as? HTMLElement ?: return
        val content = document.getElementById("integ-modal-content") as? HTMLElement ?: return
        overlay.style.display = "flex"
        content.innerHTML = buildModalHtml(provider)
        bindModalEvents(provider)
        overlay.addEventListener("click", { e -> if (e.target == overlay) closeConfigModal() })
    }

    fun closeConfigModal() {
        IntegrationsPage.activeModal = null
        (document.getElementById("integ-modal-overlay") as? HTMLElement)?.style?.display = "none"
    }

    private fun buildModalHtml(provider: ProviderInfo): String {
        val canConfig = ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)
        val readonlyAttr = if (!canConfig) "disabled" else ""
        val disabledBtn = if (!canConfig) "opacity:0.5;cursor:not-allowed;pointer-events:none;" else ""
        val fieldsHtml = ProviderConfigFields.build(provider, readonlyAttr)
        return """
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:32px;">
                <div><div style="font-size:18px;font-weight:600;">${HtmlUtils.escapeHtml(provider.name)}</div>
                <div style="font-size:11px;opacity:0.4;letter-spacing:1px;margin-top:4px;">Configuration</div></div>
                <button id="modal-btn-close" style="background:none;border:none;color:var(--text-sub);font-size:20px;cursor:pointer;padding:8px;">✕</button>
            </div>
            <div style="display:flex;flex-direction:column;gap:20px;">$fieldsHtml</div>
            <div style="display:flex;gap:12px;margin-top:24px;">
                <button id="modal-btn-test" style="flex:1;padding:16px;font-size:13px;letter-spacing:1.5px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.15);border-radius:10px;color:#fff;cursor:pointer;transition:0.2s;$disabledBtn">TEST CONNECTION</button>
                <button id="modal-btn-save" class="btn-vibrant" style="flex:1;padding:16px;font-size:13px;letter-spacing:1.5px;opacity:0.4;pointer-events:none;" disabled>SAVE</button>
            </div>
            <div id="modal-save-progress" style="display:none;margin-top:12px;"><div class="neural-loader" style="height:3px;"><div id="modal-save-bar" class="neural-progress" style="width:0%;"></div></div></div>
            <div id="modal-save-msg" style="display:none;margin-top:12px;font-size:12px;text-align:center;color:var(--primary);"></div>
        """.trimIndent()
    }

    private fun bindModalEvents(provider: ProviderInfo) {
        document.getElementById("modal-btn-close")?.addEventListener("click", { closeConfigModal() })
        if (provider.type.uppercase() == "OLLAMA") loadOllamaModels(provider.model)
        bindSliders()
        document.getElementById("modal-btn-test")?.addEventListener("click", {
            if (ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)) testConnectionInModal(provider)
        })
        document.getElementById("modal-btn-save")?.addEventListener("click", {
            saveConfigOnly(provider)
        })
    }

    private fun bindSliders() {
        val slider = document.getElementById("cfg-temperature") as? HTMLInputElement
        slider?.addEventListener("input", { (document.getElementById("cfg-temp-val") as? HTMLElement)?.textContent = slider.value })
        val tokensSlider = document.getElementById("cfg-max-tokens") as? HTMLInputElement
        tokensSlider?.addEventListener("input", { (document.getElementById("cfg-tokens-val") as? HTMLElement)?.textContent = tokensSlider.value })
    }

    private fun testConnectionInModal(provider: ProviderInfo) {
        val testBtn = document.getElementById("modal-btn-test") as? HTMLElement ?: return
        val saveBtn = document.getElementById("modal-btn-save") as? HTMLElement ?: return
        val msgEl = document.getElementById("modal-save-msg") as? HTMLElement ?: return
        val progressEl = document.getElementById("modal-save-progress") as? HTMLElement ?: return
        val bar = document.getElementById("modal-save-bar") as? HTMLElement ?: return

        testBtn.textContent = "TESTING..."; testBtn.style.opacity = "0.6"; testBtn.style.asDynamic().pointerEvents = "none"
        progressEl.style.display = "block"; bar.style.width = "0%"; msgEl.style.display = "none"
        var progress = 0
        val intervalId = window.setInterval({ progress += 5; bar.style.width = "${minOf(progress, 90)}%" }, 80)

        BlockingOverlay.show("integ-modal-content", "Testing connection...")
        IntegrationsPage.scope.launch {
            try {
                val endpoint = (document.getElementById("cfg-endpoint") as? HTMLInputElement)?.value
                val model = (document.getElementById("cfg-model") as? HTMLInputElement)?.value ?: (document.getElementById("cfg-model") as? HTMLSelectElement)?.value
                val testBody = mapOf("endpoint" to (endpoint ?: ""), "model" to (model ?: ""))
                val response = ApiClient.post("/api/integrations/${provider.providerId}/test", testBody)
                if (ApiClient.handleUnauthorized(response)) return@launch
                window.clearInterval(intervalId); bar.style.width = "100%"
                val body = response.bodyAsText()
                val result = IntegrationsPage.json.decodeFromString<TestResult>(body)
                if (result.success) {
                    msgEl.textContent = "✓ ${result.message.ifBlank { "Connection successful" }}"; msgEl.style.color = "var(--primary)"; msgEl.style.display = "block"
                    saveBtn.style.opacity = "1"; saveBtn.style.asDynamic().pointerEvents = "auto"; (saveBtn as? org.w3c.dom.HTMLButtonElement)?.disabled = false
                } else {
                    msgEl.textContent = "✗ ${result.message.ifBlank { "Connection failed" }}"; msgEl.style.color = "var(--danger)"; msgEl.style.display = "block"
                    saveBtn.style.opacity = "0.4"; saveBtn.style.asDynamic().pointerEvents = "none"
                }
            } catch (e: Exception) {
                window.clearInterval(intervalId); bar.style.width = "100%"
                msgEl.textContent = "✗ Error: ${e.message}"; msgEl.style.color = "var(--danger)"; msgEl.style.display = "block"
            } finally {
                testBtn.textContent = "TEST CONNECTION"; testBtn.style.opacity = "1"; testBtn.style.asDynamic().pointerEvents = "auto"
                BlockingOverlay.remove("integ-modal-content")
            }
        }
    }

    private fun loadOllamaModels(currentModel: String?) {
        IntegrationsPage.scope.launch {
            try {
                val response = ApiClient.get("/api/integrations/ollama/models")
                if (response.status.value == 200) {
                    val body = response.bodyAsText()
                    val modelsData = IntegrationsPage.json.decodeFromString<OllamaModelsListResponse>(body)
                    val select = document.getElementById("cfg-model") as? HTMLSelectElement ?: return@launch
                    val loadingDiv = document.getElementById("cfg-model-loading") as? HTMLElement
                    select.innerHTML = ""
                    if (modelsData.models.isEmpty()) {
                        val opt = document.createElement("option") as HTMLOptionElement
                        opt.value = currentModel ?: ""; opt.textContent = currentModel ?: "No models found"; select.appendChild(opt)
                        loadingDiv?.textContent = "No models found on Ollama server"
                    } else {
                        for (model in modelsData.models) {
                            val opt = document.createElement("option") as HTMLOptionElement
                            opt.value = model.name; opt.textContent = model.name
                            if (model.name == currentModel) opt.selected = true; select.appendChild(opt)
                        }
                        loadingDiv?.style?.display = "none"
                    }
                }
            } catch (e: Exception) {
                (document.getElementById("cfg-model-loading") as? HTMLElement)?.textContent = "Failed to load models: ${e.message}"
            }
        }
    }

    private fun saveConfigOnly(provider: ProviderInfo) {
        val progressEl = document.getElementById("modal-save-progress") as? HTMLElement ?: return
        val bar = document.getElementById("modal-save-bar") as? HTMLElement ?: return
        val msgEl = document.getElementById("modal-save-msg") as? HTMLElement ?: return
        val saveBtn = document.getElementById("modal-btn-save") as? HTMLElement ?: return

        val endpoint = (document.getElementById("cfg-endpoint") as? HTMLInputElement)?.value
        val model = (document.getElementById("cfg-model") as? HTMLInputElement)?.value ?: (document.getElementById("cfg-model") as? HTMLSelectElement)?.value ?: (document.getElementById("cfg-model-tier") as? HTMLSelectElement)?.value
        val apiKey = (document.getElementById("cfg-apikey") as? HTMLInputElement)?.value
        val temperature = (document.getElementById("cfg-temperature") as? HTMLInputElement)?.value?.toDoubleOrNull()
        val maxTokens = (document.getElementById("cfg-max-tokens") as? HTMLInputElement)?.value?.toIntOrNull()

        saveBtn.textContent = "SAVING..."; saveBtn.style.opacity = "0.6"; saveBtn.style.asDynamic().pointerEvents = "none"
        progressEl.style.display = "block"; bar.style.width = "50%"; msgEl.style.display = "none"

        BlockingOverlay.show("integ-modal-content", "Saving...")
        IntegrationsPage.scope.launch {
            try {
                val configUpdate = ConfigUpdate(endpoint = endpoint, apiKey = apiKey, model = model, temperature = temperature, maxTokens = maxTokens)
                val response = ApiClient.put("/api/integrations/${provider.providerId}/config", configUpdate)
                if (ApiClient.handleUnauthorized(response)) return@launch
                bar.style.width = "100%"
                val body = response.bodyAsText()
                val result = IntegrationsPage.json.decodeFromString<TestResult>(body)
                val effectiveStatus = if (result.success) "ACTIVE" else "OFFLINE"
                IntegrationsPage.updateCardStatus(provider.providerId, effectiveStatus, result.latencyMs)
                val idx = IntegrationsPage.providers.indexOfFirst { it.providerId == provider.providerId }
                if (idx >= 0) IntegrationsPage.providers[idx] = IntegrationsPage.providers[idx].copy(status = effectiveStatus, endpoint = endpoint ?: IntegrationsPage.providers[idx].endpoint, model = model ?: IntegrationsPage.providers[idx].model)
                msgEl.textContent = "✓ Configuration saved"; msgEl.style.color = "var(--primary)"; msgEl.style.display = "block"
                delay(1200); closeConfigModal(); IntegrationsPage.renderProviderCards()
            } catch (e: Exception) {
                bar.style.width = "100%"; msgEl.textContent = "Error: ${e.message ?: "Save failed"}"; msgEl.style.color = "var(--danger)"; msgEl.style.display = "block"
            } finally {
                saveBtn.textContent = "SAVE"; saveBtn.style.opacity = "1"; saveBtn.style.asDynamic().pointerEvents = "auto"
                BlockingOverlay.remove("integ-modal-content")
            }
        }
    }
}
