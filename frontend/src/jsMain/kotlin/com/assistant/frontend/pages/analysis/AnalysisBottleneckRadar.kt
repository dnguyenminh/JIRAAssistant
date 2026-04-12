package com.assistant.frontend.pages.analysis

import com.assistant.frontend.models.BottleneckAlert
import com.assistant.frontend.services.HtmlUtils
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Renders the bottleneck radar panel for the Analysis page.
 */
internal object AnalysisBottleneckRadar {

    fun render(bottlenecks: List<BottleneckAlert>) {
        val container = document.getElementById("bottleneckList") ?: return
        container.innerHTML = ""
        if (bottlenecks.isEmpty()) { renderEmpty(); return }
        for (alert in bottlenecks) {
            container.appendChild(createAlertElement(alert))
        }
    }

    fun renderEmpty() {
        val container = document.getElementById("bottleneckList") ?: return
        container.innerHTML = "<p style=\"opacity:0.5;font-size:12px;\">No bottleneck alerts available.</p>"
    }

    private fun createAlertElement(alert: BottleneckAlert): HTMLElement {
        val isRisk = alert.type == "RISK"
        val icon = if (isRisk) "⚠️" else "🚀"
        val borderColor = if (isRisk) "var(--danger)" else "var(--primary)"
        val severityLower = alert.severity.lowercase()
        val severityBg = severityBackground(severityLower)
        val severityColor = severityColor(severityLower)

        val el = document.createElement("div") as HTMLElement
        el.style.apply {
            display = "flex"
            asDynamic().gap = "16px"
            alignItems = "flex-start"
            padding = "16px"
            borderRadius = "12px"
            border = "1px solid var(--glass-border)"
            borderLeft = "3px solid $borderColor"
            background = "rgba(255, 255, 255, 0.02)"
        }
        el.innerHTML = """
            <span style="font-size:20px;flex-shrink:0;">$icon</span>
            <div>
                <h4 style="font-size:14px;font-weight:600;margin-bottom:4px;">${HtmlUtils.escapeHtml(alert.title)}</h4>
                <p style="font-size:12px;opacity:0.6;line-height:1.6;margin-bottom:8px;">${HtmlUtils.escapeHtml(alert.description)}</p>
                <span style="font-size:9px;font-weight:700;letter-spacing:1px;padding:3px 8px;border-radius:4px;text-transform:uppercase;background:$severityBg;color:$severityColor;">${alert.severity}</span>
            </div>
        """.trimIndent()
        return el
    }

    private fun severityBackground(severity: String) = when (severity) {
        "high" -> "rgba(255, 110, 132, 0.15)"
        "medium" -> "rgba(249, 212, 35, 0.15)"
        else -> "rgba(45, 254, 207, 0.15)"
    }

    private fun severityColor(severity: String) = when (severity) {
        "high" -> "var(--danger)"
        "medium" -> "#f9d423"
        else -> "var(--primary)"
    }
}
