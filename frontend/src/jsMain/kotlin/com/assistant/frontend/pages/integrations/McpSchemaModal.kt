package com.assistant.frontend.pages.integrations

import com.assistant.frontend.models.McpToolInfoDto
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Modal displaying MCP tool inputSchema as formatted JSON.
 * Requirements: 6.47
 */
object McpSchemaModal {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun open(tool: McpToolInfoDto) {
        close() // remove any existing
        val overlay = createOverlay()
        val modal = createModalContent(tool)
        overlay.appendChild(modal)
        document.body?.appendChild(overlay)
    }

    fun close() {
        document.getElementById("mcp-schema-overlay")?.remove()
    }

    private fun createOverlay(): HTMLElement {
        val overlay = document.createElement("div") as HTMLElement
        overlay.id = "mcp-schema-overlay"
        overlay.style.cssText = buildOverlayCss()
        overlay.addEventListener("click", { e ->
            if ((e.target as? HTMLElement)?.id == "mcp-schema-overlay") close()
        })
        return overlay
    }

    private fun createModalContent(tool: McpToolInfoDto): HTMLElement {
        val box = document.createElement("div") as HTMLElement
        box.className = "glass-card"
        box.style.cssText = "max-width:560px;width:90%;max-height:70vh;overflow-y:auto;padding:32px;"
        appendHeader(box, tool.name)
        appendSchemaBlock(box, tool)
        appendCloseButton(box)
        return box
    }

    private fun appendHeader(box: HTMLElement, name: String) {
        val header = document.createElement("div") as HTMLElement
        header.style.cssText = "display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;"
        val title = document.createElement("div") as HTMLElement
        title.textContent = "🔧 $name"
        title.style.cssText = "font-size:15px;font-weight:600;"
        val closeBtn = document.createElement("button") as HTMLElement
        closeBtn.textContent = "✕"
        closeBtn.style.cssText = "background:none;border:none;color:var(--text-sub);font-size:18px;cursor:pointer;"
        closeBtn.addEventListener("click", { close() })
        header.appendChild(title); header.appendChild(closeBtn)
        box.appendChild(header)
    }

    private fun appendSchemaBlock(box: HTMLElement, tool: McpToolInfoDto) {
        val pre = document.createElement("pre") as HTMLElement
        pre.style.cssText = "background:rgba(0,0,0,0.3);border-radius:8px;padding:16px;overflow-x:auto;font-size:11px;"
        val code = document.createElement("code") as HTMLElement
        code.textContent = formatSchema(tool)
        pre.appendChild(code)
        box.appendChild(pre)
    }

    private fun appendCloseButton(box: HTMLElement) {
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn"
        btn.textContent = "Close"
        btn.style.cssText = "margin-top:16px;width:100%;padding:10px;"
        btn.addEventListener("click", { close() })
        box.appendChild(btn)
    }

    private fun formatSchema(tool: McpToolInfoDto): String {
        val schema = tool.inputSchema
        return if (schema != null) {
            try { json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), schema) }
            catch (_: Exception) { schema.toString() }
        } else "No schema available"
    }

    private fun buildOverlayCss() = """
        position:fixed;inset:0;background:rgba(0,0,0,0.6);
        backdrop-filter:blur(8px);z-index:2500;
        display:flex;align-items:center;justify-content:center;
    """.trimIndent()
}
