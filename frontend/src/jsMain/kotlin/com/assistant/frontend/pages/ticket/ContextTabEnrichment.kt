package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.AcceptanceCriterion
import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.DependencyItem
import com.assistant.ai.deepanalysis.models.ExtractionConfidence
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Enrichment sections for Context tab: Dependencies Overview,
 * Acceptance Criteria Preview, and Analysis Info.
 * Split from ContextTabRenderer for SRP (max 200 lines/file).
 * Requirements: 19.3, 19.4, 19.5, 22.1
 */
internal object ContextTabEnrichment {

    fun renderDependenciesOverview(parent: HTMLElement, deps: DependencyInfo) {
        if (deps.blockingIssues.isEmpty() && deps.relatedIssues.isEmpty()) return
        val card = ContextTabRenderer.createSection(parent, "DEPENDENCIES OVERVIEW")
        deps.blockingIssues.forEach { card.appendChild(createBlockingRow(it)) }
        appendRelatedCount(card, deps.relatedIssues.size)
    }

    fun renderAcceptanceCriteriaPreview(
        parent: HTMLElement, criteria: List<AcceptanceCriterion>
    ) {
        if (criteria.isEmpty()) return
        val card = ContextTabRenderer.createSection(parent, "ACCEPTANCE CRITERIA")
        criteria.take(3).forEach { card.appendChild(createCriterionRow(it)) }
        if (criteria.size > 3) appendMoreLink(card, criteria.size - 3)
    }

    fun renderAnalysisInfo(parent: HTMLElement, metadata: AnalysisMetadata) {
        if (metadata.analyzedAt.isBlank() && metadata.aiProviderUsed.isBlank()) return
        val card = ContextTabRenderer.createSection(parent, "ANALYSIS INFO")
        card.appendChild(createInfoRow(metadata))
    }

    private fun createBlockingRow(item: DependencyItem): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;align-items:center;gap:10px;padding:8px 14px;" +
            "margin-bottom:6px;border-radius:8px;background:rgba(255,255,255,0.02);" +
            "border:1px solid var(--glass-border);"
        row.appendChild(styledSpan("[BLOCKING]",
            "font-size:10px;font-weight:700;letter-spacing:1px;color:#ff5050;flex-shrink:0;"))
        row.appendChild(styledSpan(item.key,
            "font-weight:600;font-size:12px;color:var(--primary);flex-shrink:0;"))
        row.appendChild(styledSpan(item.summary.ifBlank { "—" },
            "flex:1;font-size:12px;opacity:0.75;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"))
        if (item.riskLevel.isNotBlank()) row.appendChild(createRiskBadge(item.riskLevel))
        return row
    }

    private fun createRiskBadge(risk: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = risk.uppercase()
        val (bg, color) = riskLevelColors(risk)
        badge.style.cssText = "font-size:9px;font-weight:700;letter-spacing:1px;" +
            "padding:2px 8px;border-radius:4px;background:$bg;color:$color;flex-shrink:0;"
        return badge
    }

    private fun riskLevelColors(risk: String): Pair<String, String> {
        val lower = risk.lowercase()
        return when {
            lower.contains("high") -> Pair("rgba(255,80,80,0.15)", "#ff5050")
            lower.contains("medium") -> Pair("rgba(255,180,50,0.15)", "#ffb432")
            else -> Pair("rgba(45,254,207,0.12)", "var(--primary)")
        }
    }

    private fun appendRelatedCount(card: HTMLElement, count: Int) {
        if (count <= 0) return
        card.appendChild(styledSpan("($count related issues)",
            "display:inline-block;margin-top:10px;font-size:12px;opacity:0.6;font-weight:500;"))
    }

    private fun createCriterionRow(ac: AcceptanceCriterion): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;gap:12px;padding:10px 14px;margin-bottom:8px;" +
            "border-radius:8px;background:rgba(255,255,255,0.02);" +
            "border:1px solid var(--glass-border);align-items:start;"
        row.appendChild(styledSpan(ac.id.ifBlank { "—" },
            "font-size:11px;font-weight:700;color:var(--accent);min-width:40px;padding-top:2px;flex-shrink:0;"))
        row.appendChild(createCriterionBody(ac))
        return row
    }

    private fun createCriterionBody(ac: AcceptanceCriterion): Element {
        val body = document.createElement("div") as HTMLElement
        body.style.cssText = "flex:1;min-width:0;"
        body.appendChild(styledSpan(ac.description.ifBlank { "No description." },
            "font-size:13px;line-height:1.6;opacity:0.85;display:block;"))
        if (ac.testabilityAssessment.isNotBlank()) {
            body.appendChild(createTestabilityBadge(ac.testabilityAssessment))
        }
        return body
    }

    private fun createTestabilityBadge(testability: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = testability
        val (bg, color) = testabilityColors(testability)
        badge.style.cssText = "display:inline-block;margin-top:6px;font-size:10px;" +
            "font-weight:600;padding:2px 8px;border-radius:4px;background:$bg;color:$color;"
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

    private fun appendMoreLink(card: HTMLElement, remaining: Int) {
        card.appendChild(styledSpan("(+$remaining more in Complexity tab)",
            "font-size:12px;opacity:0.5;margin-top:8px;font-style:italic;display:block;"))
    }

    private fun createInfoRow(metadata: AnalysisMetadata): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;align-items:center;gap:12px;flex-wrap:wrap;"
        if (metadata.analyzedAt.isNotBlank()) {
            row.appendChild(styledSpan(
                metadata.analyzedAt.replace("T", " ").take(19), "font-size:12px;opacity:0.6;"))
        }
        if (metadata.aiProviderUsed.isNotBlank()) {
            row.appendChild(styledSpan(metadata.aiProviderUsed,
                "font-size:12px;opacity:0.6;font-weight:500;"))
        }
        row.appendChild(createConfidenceBadge(metadata.extractionConfidence))
        return row
    }

    private fun createConfidenceBadge(confidence: ExtractionConfidence): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = confidence.name
        val (bg, color) = confidenceColors(confidence)
        badge.style.cssText = "font-size:10px;font-weight:700;letter-spacing:1px;" +
            "padding:3px 10px;border-radius:4px;background:$bg;color:$color;"
        return badge
    }

    private fun confidenceColors(c: ExtractionConfidence): Pair<String, String> = when (c) {
        ExtractionConfidence.HIGH -> Pair("rgba(45,254,207,0.12)", "var(--primary)")
        ExtractionConfidence.MEDIUM -> Pair("rgba(255,180,50,0.12)", "#ffb432")
        ExtractionConfidence.LOW -> Pair("rgba(255,80,80,0.12)", "#ff5050")
    }

    private fun styledSpan(text: String, css: String): Element {
        val el = document.createElement("span") as HTMLElement
        el.textContent = text
        el.style.cssText = css
        return el
    }
}
