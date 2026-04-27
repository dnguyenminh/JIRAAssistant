package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.AffectedModule
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Renders expanded Context tab: business summary, As-Is/To-Be,
 * extracted requirements, affected modules, and technical details.
 * Requirements: 22.1
 */
internal object ContextTabRenderer {

    fun render(container: HTMLElement, data: AnalysisResponse) {
        container.innerHTML = ""
        val ctx = data.context
        val hasDeepAnalysis = !ctx?.businessSummary.isNullOrBlank()
        if (hasDeepAnalysis) {
            renderBusinessSummary(container, ctx?.businessSummary)
        } else {
            renderUnifiedSummary(container, ctx?.unified)
        }
        renderAsIsToBeSection(container, ctx?.asIsState, ctx?.toBeState)
        renderExtractedRequirements(container, ctx?.extractedRequirements)
        renderAffectedModules(container, ctx?.affectedModules)
        TechnicalDetailsRenderer.render(container, data.technicalDetails)
        DiagramRenderer.render(container, data.diagrams)
        ContextTabEnrichment.renderDependenciesOverview(container, data.dependencies)
        ContextTabEnrichment.renderAcceptanceCriteriaPreview(container, data.acceptanceCriteria)
        ContextTabEnrichment.renderAnalysisInfo(container, data.analysisMetadata)
    }

    /** Fallback for batch-scanned tickets with only unified summary. */
    private fun renderUnifiedSummary(parent: HTMLElement, unified: String?) {
        val text = unified?.takeIf { it.isNotBlank() } ?: return
        val card = createSection(parent, "REQUIREMENT SUMMARY")
        val p = document.createElement("p") as HTMLElement
        p.textContent = text
        applyBodyTextStyle(p)
        card.appendChild(p)
    }

    private fun renderBusinessSummary(parent: HTMLElement, summary: String?) {
        val text = summary?.takeIf { it.isNotBlank() } ?: return
        val card = createSection(parent, "BUSINESS SUMMARY")
        val p = document.createElement("p") as HTMLElement
        p.textContent = text
        applyBodyTextStyle(p)
        card.appendChild(p)
    }

    private fun renderAsIsToBeSection(
        parent: HTMLElement, asIs: String?, toBe: String?
    ) {
        if (asIs.isNullOrBlank() && toBe.isNullOrBlank()) return
        val card = createSection(parent, "AS-IS / TO-BE COMPARISON")
        val grid = document.createElement("div") as HTMLElement
        grid.style.cssText = "display:grid;grid-template-columns:1fr 1fr;gap:16px;"
        if (!asIs.isNullOrBlank()) grid.appendChild(createStateBox("AS-IS", asIs, "var(--accent)"))
        if (!toBe.isNullOrBlank()) grid.appendChild(createStateBox("TO-BE", toBe, "var(--primary)"))
        card.appendChild(grid)
    }

    private fun createStateBox(
        label: String, text: String, color: String
    ): Element {
        val box = document.createElement("div") as HTMLElement
        box.style.cssText = "padding:16px;border-radius:8px;background:rgba(255,255,255,0.02);border:1px solid var(--glass-border);"
        val lbl = document.createElement("div") as HTMLElement
        lbl.textContent = label
        lbl.style.cssText = "font-size:10px;font-weight:700;letter-spacing:2px;color:$color;margin-bottom:8px;"
        val p = document.createElement("p") as HTMLElement
        p.textContent = text
        applyBodyTextStyle(p)
        box.appendChild(lbl)
        box.appendChild(p)
        return box
    }

    private fun renderExtractedRequirements(
        parent: HTMLElement, reqs: List<String>?
    ) {
        if (reqs.isNullOrEmpty()) return
        val card = createSection(parent, "EXTRACTED REQUIREMENTS")
        val list = document.createElement("ul") as HTMLElement
        list.style.cssText = "margin:0;padding-left:20px;list-style:disc;"
        reqs.forEach { req ->
            val li = document.createElement("li") as HTMLElement
            li.textContent = req
            li.style.cssText = "font-size:13px;line-height:1.8;opacity:0.85;margin-bottom:4px;"
            list.appendChild(li)
        }
        card.appendChild(list)
    }

    private fun renderAffectedModules(
        parent: HTMLElement, modules: List<AffectedModule>?
    ) {
        if (modules.isNullOrEmpty()) return
        val card = createSection(parent, "AFFECTED MODULES")
        val wrap = document.createElement("div") as HTMLElement
        wrap.style.cssText = "display:flex;flex-wrap:wrap;gap:8px;"
        modules.forEach { mod -> wrap.appendChild(createModuleBadge(mod)) }
        card.appendChild(wrap)
    }

    private fun createModuleBadge(mod: AffectedModule): Element {
        val (color, bg, border) = moduleColors(mod.colorCategory)
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = mod.name
        badge.style.cssText = "display:inline-block;padding:6px 14px;border-radius:8px;font-size:11px;font-weight:600;letter-spacing:1px;background:$bg;color:$color;border:1px solid $border;"
        return badge
    }

    internal fun moduleColors(cat: String): Triple<String, String, String> = when (cat) {
        "PRIMARY" -> Triple("var(--primary)", "rgba(45,254,207,0.12)", "rgba(45,254,207,0.3)")
        "ACCENT" -> Triple("var(--accent)", "rgba(51,134,255,0.12)", "rgba(51,134,255,0.3)")
        else -> Triple("var(--secondary)", "rgba(190,157,255,0.12)", "rgba(190,157,255,0.3)")
    }

    internal fun createSection(parent: HTMLElement, title: String): HTMLElement {
        val card = document.createElement("div") as HTMLElement
        card.classList.add("glass-card")
        card.style.cssText = "padding:24px;margin-bottom:16px;"
        val header = document.createElement("div") as HTMLElement
        header.textContent = title
        header.style.cssText = "font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-bottom:16px;"
        card.appendChild(header)
        parent.appendChild(card)
        return card
    }

    internal fun applyBodyTextStyle(el: HTMLElement) {
        el.style.cssText = "font-size:14px;line-height:1.8;opacity:0.85;margin:0;"
    }
}
