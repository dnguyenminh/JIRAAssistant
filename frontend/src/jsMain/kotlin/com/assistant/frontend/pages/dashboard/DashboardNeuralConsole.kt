package com.assistant.frontend.pages.dashboard

import com.assistant.frontend.models.ConsoleEntry
import com.assistant.frontend.models.DashboardAnalysis
import com.assistant.frontend.services.HtmlUtils
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Renders the Neural Console panel on the Dashboard.
 */
internal object DashboardNeuralConsole {

    fun update(data: DashboardAnalysis) {
        val consoleEl = document.getElementById("neuralConsole") ?: return
        consoleEl.innerHTML = ""
        val entries = buildEntries(data)
        if (entries.isEmpty()) { renderEmpty(consoleEl); return }
        renderEntries(consoleEl, entries)
    }

    private fun buildEntries(data: DashboardAnalysis): List<ConsoleEntry> {
        val entries = mutableListOf<ConsoleEntry>()
        for (sprint in data.velocityTrend) {
            entries.add(ConsoleEntry("VELOCITY", "${sprint.sprintName}: ${sprint.storyPoints.asDynamic().toFixed(0)} story points"))
        }
        for (alert in data.bottlenecks) {
            val icon = if (alert.type == "RISK") "⚠" else "🚀"
            entries.add(ConsoleEntry(alert.type, "$icon ${alert.title} — ${alert.description} [${alert.severity}]"))
        }
        for (provider in data.providerStatuses) {
            val latency = if (provider.latencyMs != null) "${provider.latencyMs}ms" else "—"
            entries.add(ConsoleEntry("PROVIDER", "${provider.name}: ${provider.status} (latency: $latency)"))
        }
        return entries
    }

    private fun renderEmpty(consoleEl: org.w3c.dom.Element) {
        val line = document.createElement("div") as HTMLElement
        line.className = "console-line"; line.style.opacity = "0.3"
        line.innerHTML = "<span>No project activity data available.</span>"
        consoleEl.appendChild(line)
    }

    private fun renderEntries(consoleEl: org.w3c.dom.Element, entries: List<ConsoleEntry>) {
        val tags = mapOf(
            "VELOCITY" to "", "RISK" to "color: var(--danger);",
            "OPTIMIZATION" to "color: var(--primary);", "PROVIDER" to "color: var(--secondary);"
        )
        for ((i, entry) in entries.withIndex()) {
            val line = document.createElement("div") as HTMLElement
            line.className = "console-line"
            if (i > 4) line.style.opacity = "0.5"
            val tagStyle = tags[entry.tag] ?: ""
            line.innerHTML = """
                <span class="console-tag" style="$tagStyle">${HtmlUtils.escapeHtml(entry.tag)}</span>
                <span>${HtmlUtils.escapeHtml(entry.message)}</span>
            """.trimIndent()
            consoleEl.appendChild(line)
        }
    }
}
