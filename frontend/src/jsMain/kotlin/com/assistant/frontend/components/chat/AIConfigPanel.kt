package com.assistant.frontend.components.chat

import com.assistant.chat.UserAIConfig
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * AI Personalization config panel — summary tables with popup modal forms.
 * Requirements: 19.37, 19.38, 19.38a–19.38i, 19.40, 19.43
 */
object AIConfigPanel {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var config: UserAIConfig? = null

    private const val SKILLS_CONTAINER = "tbl-skills-container"
    private const val WORKFLOW_CONTAINER = "tbl-workflow-container"
    private const val INSTRUCTIONS_CONTAINER = "tbl-instructions-container"
    private const val RULES_CONTAINER = "tbl-rules-container"

    fun open(sidebar: HTMLElement) {
        val panel = document.createElement("div") as HTMLElement
        panel.className = "ai-config-panel"
        panel.id = "ai-config-panel"
        panel.innerHTML = buildPanelHtml()
        sidebar.appendChild(panel)
        bindPanelEvents()
        loadConfig()
    }

    fun close() {
        AIConfigPopupForm.closeModal()
        document.getElementById("ai-config-panel")?.remove()
    }

    fun updateBadge() {
        val badge = document.getElementById("chat-config-badge") ?: return
        val c = config
        if (c == null || allEmpty(c)) {
            badge.textContent = "KNOWLEDGE-AWARE CHAT"
            return
        }
        badge.textContent = formatBadge(c)
    }

    private fun formatBadge(c: UserAIConfig): String {
        val parts = mutableListOf<String>()
        if (c.skills.isNotEmpty()) parts.add("${c.skills.size} skills")
        if (c.workflow.isNotEmpty()) parts.add("${c.workflow.size} workflow")
        if (c.instructions.isNotEmpty()) parts.add("${c.instructions.size} instructions")
        if (c.rules.isNotEmpty()) parts.add("${c.rules.size} rules")
        return "⚙️ ${parts.joinToString(", ")}"
    }

    private fun allEmpty(c: UserAIConfig) =
        c.skills.isEmpty() && c.workflow.isEmpty() && c.instructions.isEmpty() && c.rules.isEmpty()

    private fun loadConfig() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/chat/config")
                if (resp.status == HttpStatusCode.OK) {
                    config = json.decodeFromString<UserAIConfig>(resp.bodyAsText())
                    populateSummaryTables()
                    updateBadge()
                }
            } catch (e: Exception) {
                console.log("[AIConfigPanel] Load failed: ${e.message}")
            }
        }
    }

    private fun saveConfig() {
        val c = UserAIConfig(
            skills = AIConfigSummaryTable.skills.toList(),
            workflow = AIConfigSummaryTable.workflow.toList(),
            instructions = AIConfigSummaryTable.instructions.toList(),
            rules = AIConfigSummaryTable.rules.toList()
        )
        BlockingOverlay.show("ai-config-panel", "Saving...")
        scope.launch {
            try {
                ApiClient.put("/api/chat/config", c)
                config = c
                updateBadge()
                close()
            } catch (e: Exception) {
                console.log("[AIConfigPanel] Save failed: ${e.message}")
            } finally {
                BlockingOverlay.remove("ai-config-panel")
            }
        }
    }

    private fun populateSummaryTables() {
        val c = config ?: return
        AIConfigSummaryTable.clearAll()
        AIConfigSummaryTable.skills.addAll(c.skills)
        AIConfigSummaryTable.workflow.addAll(c.workflow)
        AIConfigSummaryTable.instructions.addAll(c.instructions)
        AIConfigSummaryTable.rules.addAll(c.rules)
        renderAllTables()
    }

    private fun renderAllTables() {
        AIConfigSummaryTable.renderSkillsTable(SKILLS_CONTAINER)
        AIConfigSummaryTable.renderWorkflowTable(WORKFLOW_CONTAINER)
        AIConfigSummaryTable.renderInstructionsTable(INSTRUCTIONS_CONTAINER)
        AIConfigSummaryTable.renderRulesTable(RULES_CONTAINER)
    }

    private fun bindPanelEvents() {
        document.getElementById("btn-cfg-save")?.addEventListener("click", { saveConfig() })
        document.getElementById("btn-cfg-close")?.addEventListener("click", { close() })
    }

    private fun buildPanelHtml(): String {
        val header = buildHeaderHtml()
        val skills = buildSectionHtml("SKILLS", SKILLS_CONTAINER)
        val workflow = buildSectionHtml("WORKFLOW", WORKFLOW_CONTAINER)
        val instructions = buildSectionHtml("INSTRUCTIONS", INSTRUCTIONS_CONTAINER)
        val rules = buildSectionHtml("RULES", RULES_CONTAINER)
        val saveBtn = buildSaveButton()
        return "$header$skills$workflow$instructions$rules$saveBtn"
    }

    private fun buildHeaderHtml() = """
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
            <div style="font-size:15px;font-weight:700;">AI Personalization</div>
            <button id="btn-cfg-close" class="chat-close-btn">✕</button>
        </div>""".trimIndent()

    private fun buildSectionHtml(label: String, containerId: String) = """
        <div class="config-field">
            <label>$label</label>
            <div id="$containerId"></div>
        </div>""".trimIndent()

    private fun buildSaveButton() =
        """<button id="btn-cfg-save" class="btn-vibrant" style="width:100%;padding:12px;font-size:12px;letter-spacing:1.5px;">SAVE</button>"""
}
