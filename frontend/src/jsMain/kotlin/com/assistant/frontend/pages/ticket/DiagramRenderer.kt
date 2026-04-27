package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.DiagramData
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Format router for diagrams — delegates to MermaidDiagramRenderer or
 * DrawioDiagramRenderer based on DiagramData.format.
 * Requirements: 6.1, 6.2, 6.3, 6.4
 */
internal object DiagramRenderer {

    fun render(container: HTMLElement, diagrams: List<DiagramData>) {
        if (diagrams.isEmpty()) return
        val section = ContextTabRenderer.createSection(container, "DIAGRAMS")
        for (diagram in diagrams) {
            val card = createDiagramCard(section, diagram)
            routeDiagram(card, diagram)
        }
    }

    private fun createDiagramCard(section: HTMLElement, diagram: DiagramData): HTMLElement {
        val card = document.createElement("div") as HTMLElement
        card.style.cssText = "margin-bottom:16px;padding:16px;" +
            "background:rgba(255,255,255,0.02);" +
            "border:1px solid var(--glass-border);border-radius:8px;"
        val title = document.createElement("div") as HTMLElement
        title.textContent = diagram.title.ifBlank { diagram.type.uppercase() }
        title.style.cssText = "font-size:11px;font-weight:700;" +
            "letter-spacing:1.5px;opacity:0.5;margin-bottom:12px;"
        card.appendChild(title)
        section.appendChild(card)
        return card
    }

    private fun routeDiagram(card: HTMLElement, diagram: DiagramData) {
        when (diagram.format) {
            "drawio" -> DrawioDiagramRenderer.renderInCard(card, diagram)
            else -> MermaidDiagramRenderer.renderInCard(card, diagram)
        }
    }
}
