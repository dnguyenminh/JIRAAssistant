package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.McpServerInfo
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * MCP server config modal — form/JSON toggle, save, test.
 * Requirements: 6.22, 6.23, 6.24
 */
object McpConfigModal {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var editingId: String? = null
    private var isJsonMode = false

    fun open(server: McpServerInfo? = null) {
        editingId = server?.id
        val overlay = document.getElementById("mcp-modal-overlay") as? HTMLElement ?: return
        overlay.style.display = "flex"
        val title = document.getElementById("mcp-modal-title") as? HTMLElement
        title?.textContent = if (server != null) "Edit MCP Server" else "Add MCP Server"
        if (server != null) fillForm(server)
        bindEvents()
        setFormMode()
    }

    fun close() {
        val overlay = document.getElementById("mcp-modal-overlay") as? HTMLElement ?: return
        overlay.style.display = "none"
        editingId = null
        clearForm()
    }

    private fun bindEvents() {
        document.getElementById("mcp-modal-close")?.addEventListener("click", { close() })
        document.getElementById("mcp-modal-overlay")?.addEventListener("click", { e ->
            if ((e.target as? HTMLElement)?.id == "mcp-modal-overlay") close()
        })
        document.getElementById("mcp-mode-form")?.addEventListener("click", { setFormMode() })
        document.getElementById("mcp-mode-json")?.addEventListener("click", { setJsonMode() })
        document.getElementById("mcp-type")?.addEventListener("change", {
            toggleTypeFields(getSelect("mcp-type"))
        })
        document.getElementById("btn-mcp-save")?.addEventListener("click", { save() })
        document.getElementById("btn-mcp-test")?.addEventListener("click", { testConnection() })
    }

    private fun setFormMode() {
        isJsonMode = false
        (document.getElementById("mcp-form-mode") as? HTMLElement)?.style?.display = "block"
        (document.getElementById("mcp-json-mode") as? HTMLElement)?.style?.display = "none"
    }

    private fun setJsonMode() {
        isJsonMode = true
        (document.getElementById("mcp-form-mode") as? HTMLElement)?.style?.display = "none"
        (document.getElementById("mcp-json-mode") as? HTMLElement)?.style?.display = "block"
        syncFormToJson()
    }

    private fun syncFormToJson() {
        val config = readFormValues()
        val jsonStr = json.encodeToString(McpServerInfo.serializer(), config)
        (document.getElementById("mcp-json-editor") as? HTMLTextAreaElement)?.value = jsonStr
    }

    private fun save() {
        var config = if (isJsonMode) readJsonValues() else readFormValues()
        if (config.name.isBlank() && config.command.isNotBlank()) {
            config = config.copy(name = config.command)
        }
        if (config.command.isBlank() && config.type != "streamable-http") {
            showStatus("Command is required. Ensure JSON contains \"command\" field. Example: {\"stitch\": {\"command\": \"npx.cmd\", \"args\": [...]}}", true)
            return
        }
        if (config.type == "streamable-http" && config.url.isBlank()) {
            showStatus("URL is required for streamable-http type.", true)
            return
        }
        BlockingOverlay.show("mcp-modal-content", "Saving...")
        scope.launch {
            try {
                val resp = if (editingId != null) {
                    ApiClient.put("/api/integrations/mcp/$editingId", config)
                } else {
                    ApiClient.post("/api/integrations/mcp", config)
                }
                if (resp.status == HttpStatusCode.OK || resp.status == HttpStatusCode.Created) {
                    close(); McpServerCards.load()
                } else {
                    showStatus("Save failed: ${resp.bodyAsText()}", true)
                }
            } catch (e: Exception) {
                showStatus("Save failed: ${e.message}", true)
            } finally {
                BlockingOverlay.remove("mcp-modal-content")
            }
        }
    }

    private fun testConnection() {
        val id = editingId ?: return showStatus("Save first, then test.", true)
        BlockingOverlay.show("mcp-modal-content", "Testing connection...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/integrations/mcp/$id/test", mapOf<String, String>())
                val body = resp.bodyAsText()
                if (resp.status == HttpStatusCode.OK && body.contains("\"success\":true")) {
                    showStatus("✓ Connection OK", false)
                } else {
                    val error = body.substringAfter("\"error\":\"", "Test failed")
                        .substringBefore("\"")
                    showStatus("✗ $error", true)
                }
            } catch (e: Exception) {
                showStatus("✗ Test failed: ${e.message}", true)
            } finally {
                BlockingOverlay.remove("mcp-modal-content")
            }
        }
    }

    private fun readFormValues() = McpServerInfo(
        id = editingId ?: "",
        name = getInput("mcp-name"),
        type = getSelect("mcp-type"),
        command = getInput("mcp-command"),
        url = getInput("mcp-url"),
        args = getInput("mcp-args"),
        env = getTextarea("mcp-env"),
        autoApprove = getInput("mcp-auto-approve")
    )

    private fun readJsonValues(): McpServerInfo {
        var text = (document.getElementById("mcp-json-editor") as? HTMLTextAreaElement)?.value?.trim() ?: "{}"
        if (!text.startsWith("{")) text = "{$text}"
        if (!text.endsWith("}")) text = "$text}"
        
        // Use native JS JSON.parse for reliability in Kotlin/JS
        return try {
            val parsed = js("JSON.parse(text)")
            val keys = js("Object.keys(parsed)") as Array<String>
            
            // Check if first key's value is an object with "command"
            var name = ""
            var command = ""
            var args = "[]"
            var env = "{}"
            var autoApprove = "[]"
            
            if (keys.isNotEmpty()) {
                val firstKey = keys[0]
                val firstVal = parsed[firstKey]
                if (firstVal != null && js("typeof firstVal") == "object" && firstVal["command"] != null) {
                    // Named format: {"stitch": {"command":"npx",...}}
                    name = firstKey as String
                    command = (firstVal["command"] as? String) ?: ""
                    args = js("JSON.stringify(firstVal.args || [])") as String
                    env = js("JSON.stringify(firstVal.env || {})") as String
                    autoApprove = js("JSON.stringify(firstVal.autoApprove || [])") as String
                } else if (parsed["command"] != null) {
                    // Flat format: {"command":"npx","args":[...]}
                    name = (parsed["name"] as? String) ?: getInput("mcp-name")
                    command = (parsed["command"] as? String) ?: ""
                    args = js("JSON.stringify(parsed.args || [])") as String
                    env = js("JSON.stringify(parsed.env || {})") as String
                    autoApprove = js("JSON.stringify(parsed.autoApprove || [])") as String
                }
            }
            
            McpServerInfo(
                id = editingId ?: "",
                name = name,
                command = command,
                args = args,
                env = env,
                autoApprove = autoApprove
            )
        } catch (e: Exception) {
            console.log("[McpConfigModal] JSON parse error: ${e.message}")
            readFormValues()
        }
    }
    private fun fillForm(s: McpServerInfo) {
        setInput("mcp-name", s.name)
        setSelect("mcp-type", s.type)
        setInput("mcp-command", s.command)
        setInput("mcp-url", s.url)
        setInput("mcp-args", s.args)
        setTextarea("mcp-env", s.env)
        setInput("mcp-auto-approve", s.autoApprove)
        toggleTypeFields(s.type)
    }

    private fun clearForm() {
        listOf("mcp-name", "mcp-command", "mcp-args", "mcp-auto-approve").forEach { setInput(it, "") }
        setTextarea("mcp-env", "")
        hideStatus()
    }

    private fun showStatus(msg: String, isError: Boolean) {
        val el = document.getElementById("mcp-modal-status") as? HTMLElement ?: return
        el.style.display = "block"
        el.textContent = msg
        el.style.color = if (isError) "var(--danger)" else "var(--primary)"
    }

    private fun hideStatus() {
        (document.getElementById("mcp-modal-status") as? HTMLElement)?.style?.display = "none"
    }

    private fun getInput(id: String) = (document.getElementById(id) as? HTMLInputElement)?.value ?: ""
    private fun setInput(id: String, v: String) { (document.getElementById(id) as? HTMLInputElement)?.value = v }
    private fun getTextarea(id: String) = (document.getElementById(id) as? HTMLTextAreaElement)?.value ?: ""
    private fun setTextarea(id: String, v: String) { (document.getElementById(id) as? HTMLTextAreaElement)?.value = v }
    private fun getSelect(id: String) = (document.getElementById(id) as? org.w3c.dom.HTMLSelectElement)?.value ?: "stdio"
    private fun setSelect(id: String, v: String) { (document.getElementById(id) as? org.w3c.dom.HTMLSelectElement)?.value = v }

    private fun toggleTypeFields(type: String) {
        val cmdField = document.getElementById("mcp-command-field") as? HTMLElement
        val urlField = document.getElementById("mcp-url-field") as? HTMLElement
        if (type == "stdio") {
            cmdField?.style?.display = ""; urlField?.style?.display = "none"
        } else {
            cmdField?.style?.display = "none"; urlField?.style?.display = ""
        }
    }
}
