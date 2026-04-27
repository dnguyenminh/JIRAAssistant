package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.DependencyItem
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Renders Dependencies section: blocking issues with risk level badges,
 * related issues, and external dependencies.
 * Split from ComplexityTabRenderer for SRP.
 * Requirements: 22.3
 */
internal object DependencySectionRenderer {

    fun render(parent: HTMLElement, deps: DependencyInfo) {
        val hasData = deps.blockingIssues.isNotEmpty() ||
            deps.relatedIssues.isNotEmpty() ||
            deps.externalDependencies.isNotEmpty()
        if (!hasData) return
        val card = ContextTabRenderer.createSection(parent, "DEPENDENCIES")
        renderBlockingIssues(card, deps.blockingIssues)
        renderRelatedIssues(card, deps.relatedIssues)
        renderExternalDeps(card, deps.externalDependencies)
    }

    private fun renderBlockingIssues(parent: HTMLElement, items: List<DependencyItem>) {
        if (items.isEmpty()) return
        parent.appendChild(createSubLabel("BLOCKING ISSUES"))
        items.forEach { item -> parent.appendChild(createDependencyRow(item, true)) }
    }

    private fun renderRelatedIssues(parent: HTMLElement, items: List<DependencyItem>) {
        if (items.isEmpty()) return
        parent.appendChild(createSubLabel("RELATED ISSUES"))
        items.forEach { item -> parent.appendChild(createDependencyRow(item, false)) }
    }

    private fun renderExternalDeps(parent: HTMLElement, deps: List<String>) {
        if (deps.isEmpty()) return
        parent.appendChild(createSubLabel("EXTERNAL DEPENDENCIES"))
        val wrap = document.createElement("div") as HTMLElement
        wrap.style.cssText = "display:flex;flex-wrap:wrap;gap:8px;"
        deps.forEach { dep -> wrap.appendChild(createExternalBadge(dep)) }
        parent.appendChild(wrap)
    }

    private fun createDependencyRow(item: DependencyItem, showRisk: Boolean): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;align-items:center;gap:10px;padding:8px 14px;margin-bottom:6px;border-radius:8px;background:rgba(255,255,255,0.02);border:1px solid var(--glass-border);"
        row.appendChild(createTicketKey(item.key))
        row.appendChild(createSummaryText(item.summary))
        if (item.relationshipType.isNotBlank()) {
            row.appendChild(createRelationBadge(item.relationshipType))
        }
        if (showRisk && item.riskLevel.isNotBlank()) {
            row.appendChild(createRiskBadge(item.riskLevel))
        }
        return row
    }

    private fun createTicketKey(key: String): Element {
        val el = document.createElement("span") as HTMLElement
        el.textContent = key
        el.style.cssText = "font-weight:600;font-size:12px;color:var(--primary);min-width:80px;flex-shrink:0;"
        return el
    }

    private fun createSummaryText(summary: String): Element {
        val el = document.createElement("span") as HTMLElement
        el.textContent = summary.ifBlank { "—" }
        el.style.cssText = "flex:1;font-size:12px;opacity:0.75;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
        return el
    }

    private fun createRelationBadge(relation: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = relation.uppercase()
        badge.style.cssText = "font-size:9px;font-weight:700;letter-spacing:1px;padding:2px 8px;border-radius:4px;background:rgba(190,157,255,0.12);color:var(--secondary);flex-shrink:0;"
        return badge
    }

    private fun createRiskBadge(risk: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = risk.uppercase()
        val (bg, color) = riskColors(risk)
        badge.style.cssText = "font-size:9px;font-weight:700;letter-spacing:1px;padding:2px 8px;border-radius:4px;background:$bg;color:$color;flex-shrink:0;"
        return badge
    }

    private fun riskColors(risk: String): Pair<String, String> {
        val lower = risk.lowercase()
        return when {
            lower.contains("high") || lower.contains("critical") ->
                Pair("rgba(255,80,80,0.15)", "#ff5050")
            lower.contains("medium") ->
                Pair("rgba(255,180,50,0.15)", "#ffb432")
            else -> Pair("rgba(45,254,207,0.12)", "var(--primary)")
        }
    }

    private fun createExternalBadge(dep: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = dep
        badge.style.cssText = "display:inline-block;padding:6px 12px;border-radius:8px;font-size:11px;font-weight:600;background:rgba(51,134,255,0.1);border:1px solid rgba(51,134,255,0.2);color:var(--accent);"
        return badge
    }

    private fun createSubLabel(text: String): Element {
        val el = document.createElement("div") as HTMLElement
        el.textContent = text
        el.style.cssText = "font-size:10px;font-weight:700;letter-spacing:2px;opacity:0.35;margin:20px 0 10px;"
        return el
    }
}
