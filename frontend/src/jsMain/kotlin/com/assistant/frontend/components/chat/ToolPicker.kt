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
 * Tools dropdown — shows available MCP tools, inserts @tool_name.
 * Requirements: 19.56, 19.57
 */
object ToolPicker {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var tools = listOf<ToolEntry>()
    private var inputEl: HTMLTextAreaElement? = null

    @Serializable
    data class ToolEntry(val toolName: String, val serverName: String, val description: String)

    fun init(input: HTMLTextAreaElement) {
        inputEl = input
        loadTools()
    }

    fun loadTools() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/chat/tools")
                if (resp.status == HttpStatusCode.OK) {
                    tools = json.decodeFromString<List<ToolEntry>>(resp.bodyAsText())
                }
            } catch (e: Exception) {
                console.log("[ToolPicker] Load tools failed: ${e.message}")
            }
        }
    }

    fun toggle() {
        val dropdown = document.getElementById("tools-dropdown") as? HTMLElement ?: return
        if (dropdown.style.display == "none" || dropdown.style.display.isBlank()) {
            renderDropdown(dropdown, tools)
            dropdown.style.display = "block"
        } else {
            dropdown.style.display = "none"
        }
    }

    fun filterAndShow(query: String) {
        val dropdown = document.getElementById("tools-dropdown") as? HTMLElement ?: return
        val filtered = tools.filter { it.toolName.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) { dropdown.style.display = "none"; return }
        renderDropdown(dropdown, filtered)
        dropdown.style.display = "block"
    }

    fun hide() {
        (document.getElementById("tools-dropdown") as? HTMLElement)?.style?.display = "none"
    }

    private fun renderDropdown(container: HTMLElement, items: List<ToolEntry>) {
        container.innerHTML = ""
        for (tool in items) {
            val option = document.createElement("div") as HTMLElement
            option.className = "tool-option"
            option.textContent = "@${tool.toolName} — ${tool.description}"
            option.addEventListener("click", { insertTool(tool.toolName) })
            container.appendChild(option)
        }
    }

    private fun insertTool(name: String) {
        val input = inputEl ?: return
        input.value = input.value + "@$name "
        input.focus()
        hide()
    }
}
