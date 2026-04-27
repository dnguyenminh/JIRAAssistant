package com.assistant.frontend.pages.integrations

import com.assistant.frontend.models.ProviderInfo
import com.assistant.frontend.services.HtmlUtils

/**
 * Builds config field HTML for each provider type.
 */
internal object ProviderConfigFields {

    fun build(provider: ProviderInfo, disabled: String): String = when (provider.type.uppercase()) {
        "JIRA" -> buildJiraFields(provider)
        "OLLAMA" -> buildOllamaFields(provider, disabled)
        "GEMINI" -> buildGeminiFields(provider, disabled)
        "LM_STUDIO" -> buildLMStudioFields(provider, disabled)
        "GEMINI_CLI" -> buildGeminiCLIFields(provider, disabled)
        "COPILOT_CLI" -> buildCopilotCLIFields(provider, disabled)
        "KIRO_CLI" -> buildKiroCLIFields(provider, disabled)
        "EMBEDDING" -> buildEmbeddingFields(provider, disabled)
        else -> "<p style='opacity:0.5;'>No configuration available.</p>"
    }

    private fun buildJiraFields(provider: ProviderInfo): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">JIRA DOMAIN URL</label>
        <input class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "")}" readonly /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">AUTH METHOD</label>
        <input class="integ-config-input" type="text" value="API Token (OAuth 2.0)" readonly /></div>
    """.trimIndent()

    private fun buildOllamaFields(provider: ProviderInfo, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">ENDPOINT URL</label>
        <input id="cfg-endpoint" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "http://localhost:11434")}" placeholder="http://localhost:11434" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">MODEL NAME</label>
        <select id="cfg-model" class="integ-config-input integ-select" $disabled><option value="${HtmlUtils.escapeHtml(provider.model ?: "")}">${HtmlUtils.escapeHtml(provider.model ?: "Loading models...")}</option></select>
        <div id="cfg-model-loading" style="font-size:10px;opacity:0.4;margin-top:4px;">Loading available models from Ollama...</div></div>
        ${sliderField("TEMPERATURE", "cfg-temperature", "cfg-temp-val", provider.temperature ?: 0.7, "0", "1", "0.1", disabled)}
        ${sliderField("MAX TOKENS", "cfg-max-tokens", "cfg-tokens-val", provider.maxTokens ?: 4096, "256", "32768", "256", disabled)}
    """.trimIndent()

    private fun buildGeminiFields(provider: ProviderInfo, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">API KEY</label>
        <input id="cfg-apikey" class="integ-config-input" type="password" value="${HtmlUtils.escapeHtml(provider.apiKeyMasked ?: "")}" placeholder="Enter API key" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">MODEL TIER</label>
        <select id="cfg-model-tier" class="integ-select" $disabled>
            <option value="gemini-1.5-pro" ${if (provider.model == "gemini-1.5-pro") "selected" else ""}>Gemini 1.5 Pro</option>
            <option value="gemini-1.0-ultra" ${if (provider.model == "gemini-1.0-ultra") "selected" else ""}>Gemini 1.0 Ultra</option>
            <option value="gemini-1.5-flash" ${if (provider.model == "gemini-1.5-flash") "selected" else ""}>Gemini 1.5 Flash</option>
        </select></div>
        ${sliderField("TEMPERATURE", "cfg-temperature", "cfg-temp-val", provider.temperature ?: 0.7, "0", "1", "0.1", disabled)}
        ${sliderField("MAX TOKENS", "cfg-max-tokens", "cfg-tokens-val", provider.maxTokens ?: 4096, "256", "32768", "256", disabled)}
    """.trimIndent()

    private fun buildLMStudioFields(provider: ProviderInfo, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">ENDPOINT URL</label>
        <input id="cfg-endpoint" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "http://localhost:1234")}" placeholder="http://localhost:1234" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">MODEL NAME</label>
        <input id="cfg-model" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.model ?: "")}" placeholder="Model name" $disabled /></div>
        ${sliderField("TEMPERATURE", "cfg-temperature", "cfg-temp-val", provider.temperature ?: 0.7, "0", "1", "0.1", disabled)}
        ${sliderField("MAX TOKENS", "cfg-max-tokens", "cfg-tokens-val", provider.maxTokens ?: 4096, "256", "32768", "256", disabled)}
    """.trimIndent()

    private fun buildGeminiCLIFields(provider: ProviderInfo, disabled: String): String {
        val m = provider.model ?: "gemini-2.5-flash"
        return """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">CLI PATH</label>
        <input id="cfg-endpoint" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "")}" placeholder="gemini" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">MODEL</label>
        <select id="cfg-model" class="integ-select" $disabled>
            ${cliModelOption("auto", "Auto (CLI decides)", m)}
            ${cliModelOption("gemini-2.5-pro", "Gemini 2.5 Pro", m)}
            ${cliModelOption("gemini-2.5-flash", "Gemini 2.5 Flash", m)}
            ${cliModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", m)}
        </select></div>
        """.trimIndent()
    }

    private fun buildCopilotCLIFields(provider: ProviderInfo, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">CLI PATH</label>
        <input id="cfg-endpoint" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "")}" placeholder="/usr/local/bin/gh" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">MODEL NAME</label>
        <input id="cfg-model" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.model ?: "copilot")}" placeholder="copilot" $disabled /></div>
    """.trimIndent()

    private fun buildKiroCLIFields(provider: ProviderInfo, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">CLI PATH</label>
        <input id="cfg-endpoint" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "")}" placeholder="/usr/local/bin/kiro" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">MODEL NAME</label>
        <input id="cfg-model" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.model ?: "kiro")}" placeholder="kiro" $disabled /></div>
    """.trimIndent()

    private fun cliModelOption(value: String, label: String, current: String): String {
        val sel = if (current == value) "selected" else ""
        return """<option value="$value" $sel>$label</option>"""
    }

    private fun buildEmbeddingFields(provider: ProviderInfo, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">OLLAMA ENDPOINT</label>
        <input id="cfg-endpoint" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.endpoint ?: "http://localhost:11434")}" placeholder="http://localhost:11434" $disabled /></div>
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">EMBEDDING MODEL</label>
        <input id="cfg-model" class="integ-config-input" type="text" value="${HtmlUtils.escapeHtml(provider.model ?: "nomic-embed-text")}" placeholder="nomic-embed-text" $disabled /></div>
        <div style="font-size:11px;opacity:0.4;margin-top:8px;">Used for semantic search on attachment content. Run: <code>ollama pull nomic-embed-text</code></div>
    """.trimIndent()

    private fun sliderField(label: String, id: String, valId: String, value: Any, min: String, max: String, step: String, disabled: String): String = """
        <div><label style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.5;display:block;margin-bottom:6px;">$label <span id="$valId" style="color:var(--primary);">$value</span></label>
        <input id="$id" class="integ-slider" type="range" min="$min" max="$max" step="$step" value="$value" $disabled /></div>
    """.trimIndent()
}
