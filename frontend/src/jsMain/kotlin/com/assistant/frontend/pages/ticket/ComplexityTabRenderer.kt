package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.AcceptanceCriterion
import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.KBReference
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Renders expanded Complexity tab: Scrum Points + rationale,
 * dependencies + risk level, acceptance criteria + testability,
 * and KB references with similarity percentage.
 * Requirements: 22.3
 */
internal object ComplexityTabRenderer {

    fun render(container: HTMLElement, data: AnalysisResponse) {
        container.innerHTML = ""
        val complexity = data.complexity
        renderScrumPointsSection(container, complexity?.scrumPoints, complexity?.description)
        DependencySectionRenderer.render(container, data.dependencies)
        renderAcceptanceCriteria(container, data.acceptanceCriteria)
        renderKBReferences(container, complexity?.kbReferences)
    }

    private fun renderScrumPointsSection(
        parent: HTMLElement, points: Double?, rationale: String?
    ) {
        val card = ContextTabRenderer.createSection(parent, "SCRUM POINTS")
        val grid = document.createElement("div") as HTMLElement
        grid.style.cssText = "display:grid;grid-template-columns:auto 1fr;gap:24px;align-items:start;"
        grid.appendChild(createPointsDisplay(points ?: 0.0))
        grid.appendChild(createRationaleBlock(rationale))
        card.appendChild(grid)
    }

    private fun createPointsDisplay(points: Double): Element {
        val box = document.createElement("div") as HTMLElement
        box.style.cssText = "text-align:center;padding:16px 24px;"
        val value = document.createElement("div") as HTMLElement
        value.textContent = formatPoints(points)
        value.style.cssText = "font-size:48px;font-weight:700;background:linear-gradient(135deg,var(--primary),var(--accent));-webkit-background-clip:text;-webkit-text-fill-color:transparent;line-height:1;"
        val label = document.createElement("div") as HTMLElement
        label.textContent = "SP"
        label.style.cssText = "font-size:11px;font-weight:700;letter-spacing:2px;opacity:0.4;margin-top:8px;"
        box.appendChild(value)
        box.appendChild(label)
        return box
    }

    private fun createRationaleBlock(rationale: String?): Element {
        val block = document.createElement("div") as HTMLElement
        block.style.cssText = "min-width:0;"
        val sublabel = document.createElement("div") as HTMLElement
        sublabel.textContent = "RATIONALE"
        sublabel.style.cssText = "font-size:10px;font-weight:700;letter-spacing:2px;opacity:0.35;margin-bottom:8px;"
        val text = document.createElement("p") as HTMLElement
        text.textContent = rationale?.takeIf { it.isNotBlank() } ?: "No rationale provided."
        text.style.cssText = "font-size:13px;line-height:1.8;opacity:0.85;margin:0;"
        block.appendChild(sublabel)
        block.appendChild(text)
        return block
    }

    private fun renderAcceptanceCriteria(
        parent: HTMLElement, criteria: List<AcceptanceCriterion>
    ) {
        if (criteria.isEmpty()) return
        val card = ContextTabRenderer.createSection(parent, "ACCEPTANCE CRITERIA")
        criteria.forEach { ac -> card.appendChild(createCriterionItem(ac)) }
    }

    private fun createCriterionItem(ac: AcceptanceCriterion): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;gap:12px;padding:10px 14px;margin-bottom:8px;border-radius:8px;background:rgba(255,255,255,0.02);border:1px solid var(--glass-border);align-items:start;"
        row.appendChild(createCriterionId(ac.id))
        row.appendChild(createCriterionBody(ac.description, ac.testabilityAssessment))
        return row
    }

    private fun createCriterionId(id: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = id.ifBlank { "—" }
        badge.style.cssText = "font-size:11px;font-weight:700;color:var(--accent);min-width:40px;padding-top:2px;flex-shrink:0;"
        return badge
    }

    private fun createCriterionBody(desc: String, testability: String): Element {
        val body = document.createElement("div") as HTMLElement
        body.style.cssText = "flex:1;min-width:0;"
        val descEl = document.createElement("div") as HTMLElement
        descEl.textContent = desc.ifBlank { "No description." }
        descEl.style.cssText = "font-size:13px;line-height:1.6;opacity:0.85;"
        body.appendChild(descEl)
        if (testability.isNotBlank()) body.appendChild(createTestabilityBadge(testability))
        return body
    }

    private fun createTestabilityBadge(testability: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = testability
        val (bg, color) = testabilityColors(testability)
        badge.style.cssText = "display:inline-block;margin-top:6px;font-size:10px;font-weight:600;padding:2px 8px;border-radius:4px;background:$bg;color:$color;"
        return badge
    }

    private fun testabilityColors(t: String): Pair<String, String> {
        val lower = t.lowercase()
        return when {
            lower.contains("high") || lower.contains("easy") ->
                Pair("rgba(45,254,207,0.12)", "var(--primary)")
            lower.contains("low") || lower.contains("hard") ->
                Pair("rgba(255,80,80,0.12)", "#ff5050")
            else -> Pair("rgba(255,180,50,0.12)", "#ffb432")
        }
    }

    private fun renderKBReferences(
        parent: HTMLElement, refs: List<KBReference>?
    ) {
        if (refs.isNullOrEmpty()) return
        val card = ContextTabRenderer.createSection(parent, "KB REFERENCES")
        val wrap = document.createElement("div") as HTMLElement
        wrap.style.cssText = "display:flex;flex-wrap:wrap;gap:8px;"
        refs.forEach { ref -> wrap.appendChild(createKBBadge(ref)) }
        card.appendChild(wrap)
    }

    private fun createKBBadge(ref: KBReference): Element {
        val badge = document.createElement("span") as HTMLElement
        val pct = ref.similarityPercent.toInt()
        badge.textContent = "${ref.ticketId}  ${pct}%"
        badge.style.cssText = "display:inline-block;padding:6px 12px;border-radius:8px;font-size:11px;font-weight:600;background:rgba(45,254,207,0.08);border:1px solid rgba(45,254,207,0.2);color:var(--primary);"
        return badge
    }

    private fun formatPoints(points: Double): String =
        if (points == points.toLong().toDouble()) "${points.toLong()}" else "$points"
}
