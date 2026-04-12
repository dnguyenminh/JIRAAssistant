package com.assistant.frontend.components.chat

import com.assistant.chat.UserAIConfig
import com.assistant.frontend.api.ApiClient
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * AI Personalization config panel — load/save skills, workflow, instructions, rules.
 * Requirements: 19.37, 19.38, 19.40
 */
object AIConfigPanel {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var config: UserAIConfig? = null

    fun open(sidebar: HTMLElement) {
        val panel = document.createElement("div") as HTMLElement
        panel.className = "ai-config-panel"
        panel.id = "ai-config-panel"
        panel.innerHTML = buildPanelHtml()
        sidebar.appendChild(panel)
        bindPanelEvents(panel)
        loadConfig()
    }

    fun close() {
        document.getElementById("ai-config-panel")?.remove()
    }

    fun updateBadge() {
        val badge = document.getElementById("chat-config-badge") ?: return
        val c = config
        if (c == null || allEmpty(c)) {
            badge.textContent = "KNOWLEDGE-AWARE CHAT"
            return
        }
        val counts = mutableListOf<String>()
        if (c.skills.isNotBlank()) counts.add("skills")
        if (c.workflow.isNotBlank()) counts.add("workflow")
        if (c.instructions.isNotBlank()) counts.add("instructions")
        if (c.rules.isNotBlank()) counts.add("rules")
        badge.textContent = "⚙️ ${counts.size} config active"
    }

    private fun allEmpty(c: UserAIConfig) =
        c.skills.isBlank() && c.workflow.isBlank() && c.instructions.isBlank() && c.rules.isBlank()

    private fun loadConfig() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/chat/config")
                if (resp.status == HttpStatusCode.OK) {
                    config = json.decodeFromString<UserAIConfig>(resp.bodyAsText())
                    fillFields()
                    updateBadge()
                }
            } catch (e: Exception) {
                console.log("[AIConfigPanel] Load failed: ${e.message}")
            }
        }
    }

    private fun saveConfig() {
        val c = UserAIConfig(
            skills = getFieldValue("cfg-skills"),
            workflow = getFieldValue("cfg-workflow"),
            instructions = getFieldValue("cfg-instructions"),
            rules = getFieldValue("cfg-rules")
        )
        scope.launch {
            try {
                ApiClient.put("/api/chat/config", c)
                config = c
                updateBadge()
                close()
            } catch (e: Exception) {
                console.log("[AIConfigPanel] Save failed: ${e.message}")
            }
        }
    }

    private fun fillFields() {
        val c = config ?: return
        setFieldValue("cfg-skills", c.skills)
        setFieldValue("cfg-workflow", c.workflow)
        setFieldValue("cfg-instructions", c.instructions)
        setFieldValue("cfg-rules", c.rules)
    }

    private fun getFieldValue(id: String) = (document.getElementById(id) as? HTMLTextAreaElement)?.value ?: ""
    private fun setFieldValue(id: String, v: String) { (document.getElementById(id) as? HTMLTextAreaElement)?.value = v }

    private fun bindPanelEvents(panel: HTMLElement) {
        document.getElementById("btn-cfg-save")?.addEventListener("click", { saveConfig() })
        document.getElementById("btn-cfg-close")?.addEventListener("click", { close() })
    }

    private fun buildPanelHtml() = """
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
            <div style="font-size:15px;font-weight:700;">AI Personalization</div>
            <button id="btn-cfg-close" class="chat-close-btn">✕</button>
        </div>
        <div class="config-field"><label>SKILLS</label><textarea id="cfg-skills" rows="3" placeholder="e.g. Backend Java developer, Scrum Master"></textarea></div>
        <div class="config-field"><label>WORKFLOW</label><textarea id="cfg-workflow" rows="3" placeholder="e.g. Sprint 2 weeks, review PR before merge"></textarea></div>
        <div class="config-field"><label>INSTRUCTIONS</label><textarea id="cfg-instructions" rows="3" placeholder="e.g. Always reply in Vietnamese"></textarea></div>
        <div class="config-field"><label>RULES</label><textarea id="cfg-rules" rows="3" placeholder="e.g. Never delete data"></textarea></div>
        <button id="btn-cfg-save" class="btn-vibrant" style="width:100%;padding:12px;font-size:12px;letter-spacing:1.5px;">SAVE</button>
    """.trimIndent()
}
