package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.TicketPageState
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Tab switching and content rendering for analysis results.
 * Delegates actual rendering to dedicated tab renderers:
 * - ContextTabRenderer (Req 22.1)
 * - EvolutionTabRenderer (Req 22.2)
 * - ComplexityTabRenderer (Req 22.3)
 * Shows ConfidenceBadge for analysis metadata (Req 22.4, 22.5).
 */
internal object TicketResultTabs {

    var activeTab: String = "context"
    var currentAnalysis: AnalysisResponse? = null

    fun switchTab(tabName: String) {
        activeTab = tabName
        updateTabStyles()
        val data = currentAnalysis ?: return
        renderTabContent(data)
        saveTabState(tabName)
    }

    fun updateTabStyles() {
        val tabs = document.querySelectorAll(".ti-tab")
        for (i in 0 until tabs.length) {
            val tab = tabs.item(i) as? HTMLElement ?: continue
            val tabName = tab.getAttribute("data-tab") ?: ""
            applyTabStyle(tab, tabName == activeTab)
        }
    }

    fun renderTabContent(data: AnalysisResponse) {
        val contentEl = document.getElementById("ti-tab-content") as? HTMLElement ?: return
        applyFadeAnimation(contentEl)
        contentEl.innerHTML = ""
        renderMetadataBadge(contentEl, data)
        val tabContainer = createTabContainer(contentEl)
        delegateToRenderer(tabContainer, data)
        val ticketId = TicketCombobox.selectedTicket?.ticketId
        if (ticketId != null) DocumentGenerationSection.refreshBadges(ticketId)
    }

    /** Render ConfidenceBadge above tab content. Req 22.4, 22.5 */
    private fun renderMetadataBadge(parent: HTMLElement, data: AnalysisResponse) {
        val metadata = data.analysisMetadata
        if (metadata.analyzedAt.isBlank() && metadata.aiProviderUsed.isBlank()) return
        ConfidenceBadge.render(parent, metadata)
    }

    /** Create sub-container for tab renderer (renderers clear innerHTML). */
    private fun createTabContainer(parent: HTMLElement): HTMLElement {
        val div = document.createElement("div") as HTMLElement
        parent.appendChild(div)
        return div
    }

    /** Delegate to dedicated tab renderers. Req 22.1-22.3 */
    private fun delegateToRenderer(container: HTMLElement, data: AnalysisResponse) {
        when (activeTab) {
            "context" -> ContextTabRenderer.render(container, data)
            "evolution" -> EvolutionTabRenderer.render(container, data)
            "complexity" -> ComplexityTabRenderer.render(container, data)
        }
    }

    private fun applyTabStyle(tab: HTMLElement, isActive: Boolean) {
        if (isActive) {
            tab.style.background = "rgba(45,254,207,0.15)"
            tab.style.borderColor = "rgba(45,254,207,0.3)"
            tab.style.color = "var(--primary)"
            tab.classList.add("active")
        } else {
            tab.style.background = "rgba(255,255,255,0.03)"
            tab.style.borderColor = "var(--glass-border)"
            tab.style.color = "var(--text-sub)"
            tab.classList.remove("active")
        }
    }

    private fun applyFadeAnimation(el: HTMLElement) {
        el.style.animation = "none"
        el.offsetHeight // force reflow
        el.style.animation = "fadeIn 0.5s ease"
    }

    /** Persist active tab to sessionStorage. Req 23.1, 23.2 */
    private fun saveTabState(tabName: String) {
        val ticketId = TicketCombobox.selectedTicket?.ticketId ?: ""
        TicketStateManager.save(TicketPageState(
            selectedTicketId = ticketId,
            activeTab = tabName,
            analysisResult = currentAnalysis
        ))
    }
}
