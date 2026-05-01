package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.ProviderInfo
import com.assistant.frontend.pages.IntegrationsPage
import com.assistant.frontend.services.HtmlUtils
import com.assistant.rbac.Permission
import org.w3c.dom.HTMLElement

/**
 * Builds provider card HTML and binds card-level events.
 */
internal object IntegrationsCardBuilder {

    fun buildCardHtml(provider: ProviderInfo, index: Int, canConfig: Boolean): String {
        val statusClass = when (provider.status.uppercase()) {
            "ACTIVE" -> "status-dot-active"; "STANDBY" -> "status-dot-standby"; else -> "status-dot-offline"
        }
        val tooltipText = buildTooltipText(provider)
        val logo = providerLogo(provider.type)
        val disabledBtn = if (!canConfig) "opacity:0.5;cursor:not-allowed;pointer-events:none;" else ""
        val configBtnClass = "integ-btn-configure"
        val badgeClass = statusBadgeClass(provider.status)
        val badgeLabel = provider.status.uppercase()
        val isActive = provider.status.uppercase() == "ACTIVE"
        val startStopLabel = if (isActive) "STOP" else "START"
        val startStopClass = if (isActive) "btn-stop" else "btn-start"
        val startStopHtml = if (canConfig) {
            """<button class="integ-startstop-btn $startStopClass integ-btn-startstop" data-provider="${provider.providerId}">$startStopLabel</button>"""
        } else ""

        return """
            <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:20px;">
                <div style="display:flex;align-items:center;gap:14px;">
                    <div style="width:40px;height:40px;border-radius:10px;background:rgba(255,255,255,0.06);display:flex;align-items:center;justify-content:center;font-size:18px;">$logo</div>
                    <div>
                        <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;font-weight:600;">${HtmlUtils.escapeHtml(provider.name)}</span>
                            <span class="integ-status-badge $badgeClass">$badgeLabel</span>
                            $startStopHtml
                        </div>
                        <div style="font-size:11px;opacity:0.4;letter-spacing:1px;margin-top:2px;">${HtmlUtils.escapeHtml(provider.type)}</div>
                    </div>
                </div>
                <div style="display:flex;align-items:center;gap:10px;">
                    <div class="status-dot $statusClass" data-tooltip="$tooltipText" style="width:10px;height:10px;border-radius:50%;"></div>
                    <div style="display:flex;flex-direction:column;gap:2px;">
                        <button class="integ-arrow-btn integ-arrow-up" data-provider="${provider.providerId}" ${if (index == 0 || !canConfig) "disabled" else ""}>▲</button>
                        <button class="integ-arrow-btn integ-arrow-down" data-provider="${provider.providerId}" ${if (index == IntegrationsPage.providers.size - 1 || !canConfig) "disabled" else ""}>▼</button>
                    </div>
                </div>
            </div>
            <div style="font-size:10px;font-weight:700;letter-spacing:2px;opacity:0.3;margin-bottom:12px;">PRIORITY: #${index + 1}</div>
            <div style="display:flex;gap:8px;margin-top:16px;">
                <button class="btn-vibrant integ-btn-test" data-provider="${provider.providerId}" style="flex:1;padding:12px;font-size:12px;letter-spacing:1px;">TEST LINK</button>
                <button class="$configBtnClass" data-provider="${provider.providerId}" style="flex:1;padding:12px;font-size:12px;letter-spacing:1px;background:rgba(255,255,255,0.05);border:1px solid rgba(255,255,255,0.1);border-radius:10px;color:#fff;cursor:pointer;transition:0.2s;$disabledBtn">CONFIGURE</button>
            </div>
            <div class="integ-test-progress" data-provider="${provider.providerId}" style="display:none;margin-top:12px;">
                <div class="neural-loader" style="height:3px;"><div class="neural-progress integ-test-bar" style="width:0%;"></div></div>
            </div>
        """.trimIndent()
    }

    fun buildTooltipText(provider: ProviderInfo): String = when (provider.status.uppercase()) {
        "ACTIVE" -> "Latency: ${provider.latencyMs ?: "—"}ms | Session: ${provider.sessionTime ?: "—"} remaining"
        "STANDBY" -> "Ready for Synthesis | Last checked: ${provider.lastChecked ?: "—"}"
        else -> "Reason: ${provider.errorMessage ?: "Unknown"} | Last attempt: ${provider.lastChecked ?: "—"}"
    }

    private fun providerLogo(type: String): String = when (type.uppercase()) {
        "OLLAMA" -> "🦙"; "GEMINI" -> "✦"; "LM_STUDIO" -> "🧪"; "GEMINI_CLI" -> "⌨"
        "COPILOT_CLI" -> "🤖"; "KIRO_CLI" -> "🔮"; "EMBEDDING" -> "🧬"; else -> "⚡"
    }

    fun statusBadgeClass(status: String): String = when (status.uppercase()) {
        "ACTIVE" -> "badge-active"; "STANDBY" -> "badge-standby"; else -> "badge-offline"
    }

    fun bindCardEvents(card: HTMLElement, provider: ProviderInfo, index: Int, canConfig: Boolean) {
        card.querySelector(".integ-btn-test")?.addEventListener("click", { e ->
            e.stopPropagation(); IntegrationsTestLink.testProviderLink(provider.providerId)
        })
        card.querySelector(".integ-btn-configure")?.addEventListener("click", { e ->
            e.stopPropagation(); if (canConfig) IntegrationsConfigModal.openConfigModal(provider)
        })
        card.querySelector(".integ-arrow-up")?.addEventListener("click", { e ->
            e.stopPropagation(); if (canConfig && index > 0) IntegrationsPage.swapPriority(index, index - 1)
        })
        card.querySelector(".integ-arrow-down")?.addEventListener("click", { e ->
            e.stopPropagation(); if (canConfig && index < IntegrationsPage.providers.size - 1) IntegrationsPage.swapPriority(index, index + 1)
        })
        card.querySelector(".integ-btn-startstop")?.addEventListener("click", { e ->
            e.stopPropagation()
            if (canConfig) IntegrationsStartStop.toggle(provider, card)
        })
        if (canConfig) bindDragDrop(card, provider)
    }

    private fun bindDragDrop(card: HTMLElement, provider: ProviderInfo) {
        card.addEventListener("dragstart", { e ->
            e.asDynamic().dataTransfer.setData("text/plain", provider.providerId)
            e.asDynamic().dataTransfer.effectAllowed = "move"; card.style.opacity = "0.5"
        })
        card.addEventListener("dragend", { card.style.opacity = "1" })
        card.addEventListener("dragover", { e -> e.preventDefault(); e.asDynamic().dataTransfer.dropEffect = "move"; card.style.borderColor = "rgba(45,254,207,0.4)" })
        card.addEventListener("dragleave", { card.style.borderColor = "" })
        card.addEventListener("drop", { e ->
            e.preventDefault(); card.style.borderColor = ""
            val draggedId = e.asDynamic().dataTransfer.getData("text/plain") as String
            if (draggedId != provider.providerId) {
                val fromIdx = IntegrationsPage.providers.indexOfFirst { it.providerId == draggedId }
                val toIdx = IntegrationsPage.providers.indexOfFirst { it.providerId == provider.providerId }
                if (fromIdx >= 0 && toIdx >= 0) IntegrationsPage.swapPriority(fromIdx, toIdx)
            }
        })
    }
}
