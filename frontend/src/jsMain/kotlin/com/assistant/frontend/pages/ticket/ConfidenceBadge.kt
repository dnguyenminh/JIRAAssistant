package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.ExtractionConfidence
import com.assistant.frontend.models.ConfidenceDisplay
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Reusable component showing analysis metadata:
 * confidence level badge (color-coded), analyzed timestamp,
 * AI provider name, and LOW confidence warning.
 * Requirements: 22.4, 22.5
 */
internal object ConfidenceBadge {

    fun render(parent: HTMLElement, metadata: AnalysisMetadata) {
        val display = ConfidenceDisplay.from(metadata.extractionConfidence)
        val container = createContainer()
        container.appendChild(createConfidenceBadge(display))
        appendProviderInfo(container, metadata.aiProviderUsed)
        appendTimestamp(container, metadata.analyzedAt)
        if (display.showWarning) {
            parent.appendChild(createWarningBanner())
        }
        parent.appendChild(container)
    }

    private fun createContainer(): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.style.cssText = "display:flex;align-items:center;gap:12px;" +
            "flex-wrap:wrap;padding:12px 16px;border-radius:8px;" +
            "background:rgba(255,255,255,0.02);" +
            "border:1px solid var(--glass-border);margin-bottom:16px;"
        return el
    }

    private fun createConfidenceBadge(display: ConfidenceDisplay): HTMLElement {
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = display.label
        badge.style.cssText = "display:inline-block;padding:4px 12px;" +
            "border-radius:6px;font-size:11px;font-weight:700;" +
            "letter-spacing:1px;color:${display.cssColor};" +
            "background:${display.cssBg};" +
            "border:1px solid ${display.cssBorder};"
        return badge
    }

    private fun appendProviderInfo(parent: HTMLElement, provider: String) {
        if (provider.isBlank()) return
        val el = document.createElement("span") as HTMLElement
        el.textContent = provider
        el.style.cssText = "font-size:12px;opacity:0.6;font-weight:500;"
        parent.appendChild(createSeparator())
        parent.appendChild(el)
    }

    private fun appendTimestamp(parent: HTMLElement, timestamp: String) {
        if (timestamp.isBlank()) return
        val el = document.createElement("span") as HTMLElement
        el.textContent = formatTimestamp(timestamp)
        el.style.cssText = "font-size:12px;opacity:0.5;"
        parent.appendChild(createSeparator())
        parent.appendChild(el)
    }

    private fun createSeparator(): HTMLElement {
        val sep = document.createElement("span") as HTMLElement
        sep.textContent = "·"
        sep.style.cssText = "opacity:0.3;font-size:14px;"
        return sep
    }

    private fun createWarningBanner(): HTMLElement {
        val banner = document.createElement("div") as HTMLElement
        banner.style.cssText = "display:flex;align-items:center;gap:8px;" +
            "padding:10px 16px;border-radius:8px;margin-bottom:16px;" +
            "background:rgba(255,80,80,0.08);" +
            "border:1px solid rgba(255,80,80,0.25);"
        banner.appendChild(createWarningIcon())
        banner.appendChild(createWarningText())
        return banner
    }

    private fun createWarningIcon(): HTMLElement {
        val icon = document.createElement("span") as HTMLElement
        icon.textContent = "⚠"
        icon.style.cssText = "font-size:14px;color:#ff5050;flex-shrink:0;"
        return icon
    }

    private fun createWarningText(): HTMLElement {
        val text = document.createElement("span") as HTMLElement
        text.textContent = "Low extraction confidence — " +
            "ticket description may lack structured content. " +
            "Consider re-analyzing after updating the ticket."
        text.style.cssText = "font-size:12px;color:#ff5050;opacity:0.9;" +
            "line-height:1.5;"
        return text
    }

    private fun formatTimestamp(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.replace("T", " ").take(19)
    }
}
