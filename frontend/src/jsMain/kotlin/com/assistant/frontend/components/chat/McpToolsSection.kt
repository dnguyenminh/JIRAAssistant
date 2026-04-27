package com.assistant.frontend.components.chat

import com.assistant.frontend.api.ApiClient
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * MCP Tools Available — collapsible section showing active MCP tools.
 * Click tool → insert @tool_name into textarea at cursor position.
 * Requirements: 19.56, 19.56a, 19.56b, 19.57
 */
object McpToolsSection {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var inputEl: HTMLTextAreaElement? = null
    private var headerEl: HTMLElement? = null
    private var bodyEl: HTMLElement? = null
    private var countEl: HTMLElement? = null
    private var arrowEl: HTMLElement? = null

    @Serializable
    data class ToolInfo(val toolName: String, val serverName: String, val description: String)

    fun init(input: HTMLTextAreaElement) {
        inputEl = input
        cacheElements()
        bindToggle()
    }

    fun load() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/chat/tools")
                if (resp.status == HttpStatusCode.OK) {
                    val tools = json.decodeFromString<List<ToolInfo>>(resp.bodyAsText())
                    if (tools.isEmpty()) renderEmptyState() else renderTools(tools)
                    updateCount(tools.size)
                } else {
                    renderEmptyState()
                }
            } catch (e: Exception) {
                console.log("[McpToolsSection] Load failed: ${e.message}")
                renderEmptyState()
            }
        }
    }

    private fun cacheElements() {
        headerEl = document.getElementById("mcp-tools-toggle") as? HTMLElement
        bodyEl = document.getElementById("mcp-tools-list") as? HTMLElement
        countEl = document.getElementById("mcp-tools-count") as? HTMLElement
        arrowEl = headerEl?.querySelector(".toggle-arrow") as? HTMLElement
    }

    private fun bindToggle() {
        headerEl?.addEventListener("click", {
            bodyEl?.classList?.toggle("expanded")
            arrowEl?.classList?.toggle("expanded")
        })
    }

    private fun updateCount(count: Int) {
        countEl?.textContent = "($count)"
    }

    private fun renderTools(tools: List<ToolInfo>) {
        val body = bodyEl ?: return
        body.innerHTML = ""
        val grouped = tools.groupBy { it.serverName }
        for ((server, serverTools) in grouped) {
            body.appendChild(createGroupHeader(server))
            for (tool in serverTools) {
                body.appendChild(createToolItem(tool))
            }
        }
    }

    private fun createGroupHeader(serverName: String): HTMLElement {
        val header = document.createElement("div") as HTMLElement
        header.className = "mcp-tool-group-header"
        header.textContent = serverName
        return header
    }

    private fun createToolItem(tool: ToolInfo): HTMLElement {
        val item = document.createElement("div") as HTMLElement
        item.className = "mcp-tool-item"
        val nameSpan = document.createElement("span") as HTMLElement
        nameSpan.className = "tool-name"
        nameSpan.textContent = tool.toolName
        val descSpan = document.createElement("span") as HTMLElement
        descSpan.className = "tool-desc"
        descSpan.textContent = tool.description
        item.appendChild(nameSpan)
        item.appendChild(descSpan)
        item.addEventListener("click", { insertToolAtCursor(tool.toolName) })
        return item
    }

    private fun renderEmptyState() {
        val body = bodyEl ?: return
        body.innerHTML = ""
        val empty = document.createElement("div") as HTMLElement
        empty.className = "mcp-tools-empty"
        empty.textContent = "Không có MCP tools khả dụng"
        body.appendChild(empty)
    }

    private fun insertToolAtCursor(toolName: String) {
        val input = inputEl ?: return
        val start = input.selectionStart ?: input.value.length
        val end = input.selectionEnd ?: start
        val before = input.value.substring(0, start)
        val after = input.value.substring(end)
        val mention = "@$toolName "
        input.value = before + mention + after
        val newPos = start + mention.length
        input.setSelectionRange(newPos, newPos)
        input.focus()
    }
}
