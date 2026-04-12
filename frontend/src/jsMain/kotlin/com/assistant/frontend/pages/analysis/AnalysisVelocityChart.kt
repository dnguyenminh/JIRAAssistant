package com.assistant.frontend.pages.analysis

import com.assistant.frontend.models.SprintVelocity
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Renders the velocity trend bar chart for the Analysis page.
 */
internal object AnalysisVelocityChart {

    fun render(velocityTrend: List<SprintVelocity>) {
        val container = document.getElementById("velocityBarContainer") ?: return
        container.innerHTML = ""
        if (velocityTrend.isEmpty()) { renderEmpty(); return }

        val maxPoints = velocityTrend.maxOfOrNull { it.storyPoints }?.coerceAtLeast(1.0) ?: 1.0
        for (sprint in velocityTrend) {
            val bar = createBar(sprint, maxPoints)
            container.appendChild(bar)
        }
    }

    fun renderEmpty() {
        val container = document.getElementById("velocityBarContainer") ?: return
        container.innerHTML = "<p style=\"opacity:0.3;font-size:12px;margin:auto;\">No velocity data available.</p>"
    }

    private fun createBar(sprint: SprintVelocity, maxPoints: Double): HTMLElement {
        val heightPercent = (sprint.storyPoints / maxPoints) * 100
        val bar = document.createElement("div") as HTMLElement
        applyBarStyles(bar, heightPercent)

        val spDisplay = sprint.storyPoints.asDynamic().toFixed(0) as String
        bar.setAttribute("data-tooltip", "${sprint.sprintName}: $spDisplay SP")

        bar.appendChild(createLabel(sprint.sprintName))
        val value = createValueLabel(spDisplay)
        bar.appendChild(value)
        bindHoverEffects(bar, value)
        return bar
    }

    private fun applyBarStyles(bar: HTMLElement, heightPercent: Double) {
        bar.style.apply {
            flex = "1"
            height = "${heightPercent}%"
            background = "linear-gradient(180deg, var(--primary), var(--accent))"
            borderRadius = "6px 6px 0 0"
            position = "relative"
            minHeight = "4px"
            cursor = "pointer"
            display = "flex"
            flexDirection = "column"
            justifyContent = "flex-end"
            alignItems = "center"
            transition = "transform 0.3s ease, opacity 0.3s ease"
            transformOrigin = "bottom"
        }
    }

    private fun createLabel(sprintName: String): HTMLElement {
        val label = document.createElement("span") as HTMLElement
        label.textContent = sprintName.replace("Sprint ", "S")
        label.style.apply {
            position = "absolute"; bottom = "-24px"
            fontSize = "10px"; opacity = "0.4"; whiteSpace = "nowrap"
        }
        return label
    }

    private fun createValueLabel(spDisplay: String): HTMLElement {
        val value = document.createElement("span") as HTMLElement
        value.textContent = spDisplay
        value.style.apply {
            fontSize = "11px"; fontWeight = "700"
            opacity = "0"; transition = "opacity 0.2s ease"
            marginBottom = "4px"
        }
        return value
    }

    private fun bindHoverEffects(bar: HTMLElement, value: HTMLElement) {
        bar.addEventListener("mouseenter", {
            bar.style.transform = "scaleY(1.05)"
            bar.style.opacity = "0.85"
            value.style.opacity = "1"
        })
        bar.addEventListener("mouseleave", {
            bar.style.transform = "scaleY(1)"
            bar.style.opacity = "1"
            value.style.opacity = "0"
        })
    }
}
