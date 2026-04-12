package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.services.HtmlUtils
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Tab switching and content rendering for analysis results.
 */
internal object TicketResultTabs {

    var activeTab: String = "context"
    var currentAnalysis: AnalysisResponse? = null

    fun switchTab(tabName: String) {
        activeTab = tabName
        updateTabStyles()
        val data = currentAnalysis ?: return
        renderTabContent(data)
    }

    fun updateTabStyles() {
        val tabs = document.querySelectorAll(".ti-tab")
        for (i in 0 until tabs.length) {
            val tab = tabs.item(i) as? HTMLElement ?: continue
            val tabName = tab.getAttribute("data-tab") ?: ""
            if (tabName == activeTab) {
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
    }

    fun renderTabContent(data: AnalysisResponse) {
        val contentEl = document.getElementById("ti-tab-content") as? HTMLElement ?: return
        contentEl.style.animation = "none"
        contentEl.offsetHeight // force reflow
        contentEl.style.animation = "fadeIn 0.5s ease"
        when (activeTab) {
            "context" -> renderContextTab(contentEl, data)
            "evolution" -> renderEvolutionTab(contentEl, data)
            "complexity" -> renderComplexityTab(contentEl, data)
        }
    }

    private fun renderContextTab(container: HTMLElement, data: AnalysisResponse) {
        val ctx = data.context
        val summaryText = HtmlUtils.escapeHtml(
            if (ctx?.unified.isNullOrBlank()) "No requirement summary available." else ctx?.unified ?: ""
        )
        val modules = ctx?.affectedModules ?: emptyList()
        val moduleBadges = modules.joinToString("") { mod ->
            val (color, bg, border) = moduleColors(mod.colorCategory)
            "<span style=\"display:inline-block;padding:6px 14px;border-radius:8px;font-size:11px;font-weight:600;letter-spacing:1px;margin:4px;background:$bg;color:$color;border:1px solid $border;\">${HtmlUtils.escapeHtml(mod.name)}</span>"
        }
        container.innerHTML = """
            <div class="glass-card" style="padding:32px;">
                <div style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-bottom:16px;">REQUIREMENT SUMMARY</div>
                <p style="font-size:14px;line-height:1.8;opacity:0.85;">$summaryText</p>
                ${if (modules.isNotEmpty()) """
                <div style="margin-top:24px;">
                    <div style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-bottom:12px;">AFFECTED MODULES</div>
                    <div style="display:flex;flex-wrap:wrap;gap:4px;">$moduleBadges</div>
                </div>""" else ""}
            </div>
        """.trimIndent()
    }

    private fun moduleColors(cat: String): Triple<String, String, String> = when (cat) {
        "PRIMARY" -> Triple("var(--primary)", "rgba(45,254,207,0.12)", "rgba(45,254,207,0.3)")
        "ACCENT" -> Triple("var(--accent)", "rgba(51,134,255,0.12)", "rgba(51,134,255,0.3)")
        else -> Triple("var(--secondary)", "rgba(190,157,255,0.12)", "rgba(190,157,255,0.3)")
    }

    private fun renderEvolutionTab(container: HTMLElement, data: AnalysisResponse) {
        val entries = data.evolution
        val lines = if (entries.isEmpty()) {
            "<div class=\"console-line\" style=\"opacity:0.5;\"><span>No evolution history available.</span></div>"
        } else {
            entries.joinToString("") { entry ->
                val tag = when (entry.changeType) { "ORIGIN" -> "ORIGIN"; "CURRENT" -> "CURRENT"; else -> "v${entry.version}" }
                val tagColor = when (entry.changeType) { "ORIGIN" -> "var(--accent)"; "CURRENT" -> "var(--primary)"; else -> "var(--secondary)" }
                """<div class="console-line"><span class="console-time">[${HtmlUtils.escapeHtml(entry.date)}]</span><span class="console-tag" style="color:$tagColor;">${HtmlUtils.escapeHtml(tag)}</span><span>${HtmlUtils.escapeHtml(entry.description)}</span></div>"""
            }
        }
        container.innerHTML = """<div class="neural-console" style="height:auto;min-height:200px;max-height:400px;">$lines</div>"""
    }

    private fun renderComplexityTab(container: HTMLElement, data: AnalysisResponse) {
        val complexity = data.complexity
        val scrumPoints = complexity?.scrumPoints ?: 0.0
        val description = HtmlUtils.escapeHtml(complexity?.description ?: "No complexity assessment available.")
        val kbRefs = complexity?.kbReferences ?: emptyList()
        val kbBadges = kbRefs.joinToString("") { ref ->
            "<span style=\"display:inline-block;padding:6px 12px;border-radius:8px;font-size:11px;font-weight:600;margin:4px;background:rgba(45,254,207,0.08);border:1px solid rgba(45,254,207,0.2);color:var(--primary);\">${HtmlUtils.escapeHtml(ref.ticketId)} <span style=\"opacity:0.6;\">${ref.similarityPercent.toInt()}%</span></span>"
        }
        val isKbHit = data.source == "KB_CACHE"
        val pointDisplay = if (scrumPoints == scrumPoints.toLong().toDouble()) "${scrumPoints.toLong()}" else "$scrumPoints"
        val level = when { scrumPoints <= 0 -> "N/A"; scrumPoints <= 3 -> "Low"; scrumPoints <= 8 -> "Medium"; scrumPoints <= 13 -> "High"; else -> "Very High" }

        container.innerHTML = """
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;">
                <div class="glass-card" style="padding:32px;"><div class="calibration-grid"><div class="score-display"><div class="score-val">$pointDisplay</div><div style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-top:8px;">SCRUM POINTS</div></div></div></div>
                <div class="glass-card" style="padding:32px;">
                    <div style="font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-bottom:12px;">COMPLEXITY LEVEL</div>
                    <div style="font-size:24px;font-weight:300;margin-bottom:16px;color:var(--secondary);">${HtmlUtils.escapeHtml(level)}</div>
                    <p style="font-size:13px;line-height:1.7;opacity:0.75;margin-bottom:16px;">$description</p>
                    ${if (kbRefs.isNotEmpty()) """<div style="margin-top:20px;"><div style="font-size:10px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-bottom:8px;">KB REFERENCES</div><div style="display:flex;flex-wrap:wrap;">$kbBadges</div></div>"""
                    else if (isKbHit) """<div style="margin-top:20px;"><span style="display:inline-block;padding:6px 12px;border-radius:8px;font-size:11px;font-weight:600;background:rgba(45,254,207,0.08);border:1px solid rgba(45,254,207,0.2);color:var(--primary);">KB HIT ✓</span></div>"""
                    else ""}
                </div>
            </div>
        """.trimIndent()
    }
}
