package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.EvolutionEntry
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Renders expanded Evolution tab: neural console timeline
 * with changelog detail — version, date, description,
 * changeType badge, and impact assessment.
 * Requirements: 22.2
 */
internal object EvolutionTabRenderer {

    fun render(container: HTMLElement, data: AnalysisResponse) {
        container.innerHTML = ""
        val entries = data.evolution
        if (entries.isEmpty()) {
            renderEmptyState(container)
            return
        }
        val console = createConsoleContainer(container)
        entries.forEachIndexed { idx, entry ->
            console.appendChild(createTimelineEntry(entry, idx, entries.size))
        }
    }

    private fun renderEmptyState(parent: HTMLElement) {
        val card = ContextTabRenderer.createSection(parent, "EVOLUTION TIMELINE")
        val msg = document.createElement("div") as HTMLElement
        msg.textContent = "No evolution history available."
        msg.style.cssText = "font-size:13px;opacity:0.5;padding:16px 0;"
        card.appendChild(msg)
    }

    private fun createConsoleContainer(parent: HTMLElement): HTMLElement {
        val wrapper = document.createElement("div") as HTMLElement
        wrapper.classList.add("neural-console")
        wrapper.style.cssText = "height:auto;min-height:200px;max-height:600px;overflow-y:auto;padding:20px;"
        parent.appendChild(wrapper)
        return wrapper
    }

    private fun createTimelineEntry(
        entry: EvolutionEntry, index: Int, total: Int
    ): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = buildEntryStyle(index < total - 1)
        row.appendChild(createTimelineDot(entry.changeType))
        row.appendChild(createEntryContent(entry))
        return row
    }

    private fun buildEntryStyle(hasConnector: Boolean): String {
        val border = if (hasConnector) "border-left:2px solid rgba(255,255,255,0.08);" else ""
        return "display:flex;gap:16px;padding:0 0 24px 0;margin-left:8px;${border}"
    }

    private fun createTimelineDot(changeType: String): Element {
        val dot = document.createElement("div") as HTMLElement
        val color = changeTypeColor(changeType)
        dot.style.cssText = "width:12px;height:12px;border-radius:50%;background:$color;margin-top:4px;margin-left:-7px;flex-shrink:0;box-shadow:0 0 8px $color;"
        return dot
    }

    private fun createEntryContent(entry: EvolutionEntry): Element {
        val content = document.createElement("div") as HTMLElement
        content.style.cssText = "flex:1;min-width:0;"
        content.appendChild(createEntryHeader(entry))
        content.appendChild(createEntryDescription(entry.description))
        return content
    }

    private fun createEntryHeader(entry: EvolutionEntry): Element {
        val header = document.createElement("div") as HTMLElement
        header.style.cssText = "display:flex;align-items:center;gap:10px;margin-bottom:8px;flex-wrap:wrap;"
        header.appendChild(createChangeTypeBadge(entry.changeType))
        header.appendChild(createVersionLabel(entry.version))
        header.appendChild(createDateLabel(entry.date))
        return header
    }

    private fun createChangeTypeBadge(changeType: String): Element {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = formatChangeType(changeType)
        val (bg, color) = changeTypeBadgeColors(changeType)
        badge.style.cssText = "font-size:10px;font-weight:700;letter-spacing:1.5px;padding:3px 10px;border-radius:4px;background:$bg;color:$color;"
        return badge
    }

    private fun createVersionLabel(version: String): Element {
        val label = document.createElement("span") as HTMLElement
        label.textContent = if (version.startsWith("v")) version else "v$version"
        label.style.cssText = "font-size:12px;font-weight:600;color:var(--text-main);opacity:0.9;"
        return label
    }

    private fun createDateLabel(date: String): Element {
        val label = document.createElement("span") as HTMLElement
        label.textContent = date
        label.style.cssText = "font-size:11px;opacity:0.4;font-family:'JetBrains Mono',monospace;"
        return label
    }

    private fun createEntryDescription(description: String): Element {
        val desc = document.createElement("p") as HTMLElement
        desc.textContent = description.ifBlank { "No description." }
        desc.style.cssText = "font-size:13px;line-height:1.7;opacity:0.8;margin:0;"
        return desc
    }

    private fun formatChangeType(type: String): String = when (type) {
        "ORIGIN" -> "ORIGIN"
        "CURRENT" -> "CURRENT"
        "UPDATE" -> "UPDATE"
        else -> type.uppercase()
    }

    private fun changeTypeColor(type: String): String = when (type) {
        "ORIGIN" -> "var(--accent)"
        "CURRENT" -> "var(--primary)"
        else -> "var(--secondary)"
    }

    private fun changeTypeBadgeColors(type: String): Pair<String, String> = when (type) {
        "ORIGIN" -> Pair("rgba(51,134,255,0.15)", "var(--accent)")
        "CURRENT" -> Pair("rgba(45,254,207,0.15)", "var(--primary)")
        else -> Pair("rgba(190,157,255,0.15)", "var(--secondary)")
    }
}
