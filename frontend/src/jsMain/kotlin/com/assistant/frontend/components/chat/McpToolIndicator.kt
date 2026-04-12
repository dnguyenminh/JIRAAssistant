package com.assistant.frontend.components.chat

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Tool execution indicator + collapsible result block in AI Chat.
 * Requirements: 6.54
 */
object McpToolIndicator {

    fun createRunningIndicator(toolName: String): HTMLElement {
        val wrapper = document.createElement("div") as HTMLElement
        wrapper.className = "chat-message assistant mcp-tool-indicator"
        val bubble = document.createElement("div") as HTMLElement
        bubble.className = "chat-bubble"
        bubble.style.cssText = "opacity:0.7;font-size:12px;display:flex;align-items:center;gap:6px;"
        bubble.innerHTML = "🔧 <span>Đang gọi <strong>$toolName</strong>...</span>"
        wrapper.appendChild(bubble)
        return wrapper
    }

    fun createResultBlock(toolName: String, result: String, isError: Boolean): HTMLElement {
        val wrapper = document.createElement("div") as HTMLElement
        wrapper.className = "chat-message assistant mcp-tool-result"
        val bubble = document.createElement("div") as HTMLElement
        bubble.className = "chat-bubble"
        appendToggleHeader(bubble, toolName, isError)
        appendCollapsibleContent(bubble, result)
        wrapper.appendChild(bubble)
        return wrapper
    }

    private fun appendToggleHeader(bubble: HTMLElement, toolName: String, isError: Boolean) {
        val header = document.createElement("div") as HTMLElement
        header.style.cssText = "cursor:pointer;font-size:11px;display:flex;align-items:center;gap:6px;"
        val icon = if (isError) "❌" else "✅"
        header.innerHTML = "$icon <strong>$toolName</strong> <span style='opacity:0.4;'>▶ Show result</span>"
        header.addEventListener("click", {
            val content = bubble.querySelector(".mcp-tool-content") as? HTMLElement ?: return@addEventListener
            val visible = content.style.display != "none"
            content.style.display = if (visible) "none" else "block"
            val label = if (visible) "▶ Show result" else "▼ Hide result"
            header.innerHTML = "$icon <strong>$toolName</strong> <span style='opacity:0.4;'>$label</span>"
        })
        bubble.appendChild(header)
    }

    private fun appendCollapsibleContent(bubble: HTMLElement, result: String) {
        val content = document.createElement("div") as HTMLElement
        content.className = "mcp-tool-content"
        content.style.display = "none"
        val pre = document.createElement("pre") as HTMLElement
        pre.style.cssText = "background:rgba(0,0,0,0.3);border-radius:6px;padding:10px;font-size:10px;margin-top:8px;max-height:200px;overflow:auto;white-space:pre-wrap;"
        val code = document.createElement("code") as HTMLElement
        code.textContent = result
        pre.appendChild(code)
        content.appendChild(pre)
        bubble.appendChild(content)
    }
}
