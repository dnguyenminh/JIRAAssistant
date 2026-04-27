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
 * AI Personalization config panel — editable tables for skills, workflow, instructions, rules.
 * Requirements: 19.37, 19.38, 19.38a, 19.38b, 19.38c, 19.40, 19.43
 */
object AIConfigPanel {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var config: UserAIConfig? = null

    private const val TBL_SKILLS = "tbl-skills"
    private const val TBL_WORKFLOW = "tbl-workflow"
    private const val TBL_INSTRUCTIONS = "tbl-instructions"
    private const val TBL_RULES = "tbl-rules"

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
                    populateTables()
                    updateBadge()
                }
            } catch (e: Exception) {
                console.log("[AIConfigPanel] Load failed: ${e.message}")
            }
        }
    }

    private fun saveConfig() {
        val c = UserAIConfig(
            skills = AIConfigTableBuilder.collectSkills(TBL_SKILLS),
            workflow = AIConfigTableBuilder.collectWorkflow(TBL_WORKFLOW),
            instructions = AIConfigTableBuilder.collectInstructions(TBL_INSTRUCTIONS),
            rules = AIConfigTableBuilder.collectRules(TBL_RULES)
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

    private fun populateTables() {
        val c = config ?: return
        c.skills.forEach { AIConfigTableBuilder.addSkillRow(TBL_SKILLS, it) }
        c.workflow.forEach { AIConfigTableBuilder.addWorkflowRow(TBL_WORKFLOW, it) }
        c.instructions.forEach { AIConfigTableBuilder.addInstructionRow(TBL_INSTRUCTIONS, it) }
        c.rules.forEach { AIConfigTableBuilder.addRuleRow(TBL_RULES, it) }
    }

    private fun bindPanelEvents() {
        document.getElementById("btn-cfg-save")?.addEventListener("click", { saveConfig() })
        document.getElementById("btn-cfg-close")?.addEventListener("click", { close() })
        bindAddRowButtons()
    }

    private fun bindAddRowButtons() {
        val buttons = document.querySelectorAll(".btn-add-row")
        for (i in 0 until buttons.length) {
            val el = buttons.item(i) as? HTMLElement ?: continue
            el.addEventListener("click", {
                val tableId = el.getAttribute("data-table") ?: return@addEventListener
                addRowToTable(tableId)
            })
        }
    }

    private fun addRowToTable(tableId: String) {
        when (tableId) {
            TBL_SKILLS -> AIConfigTableBuilder.addSkillRow(tableId)
            TBL_WORKFLOW -> AIConfigTableBuilder.addWorkflowRow(tableId)
            TBL_INSTRUCTIONS -> AIConfigTableBuilder.addInstructionRow(tableId)
            TBL_RULES -> AIConfigTableBuilder.addRuleRow(tableId)
        }
    }

    private fun buildPanelHtml(): String {
        val header = buildHeaderHtml()
        val skills = buildSectionHtml("SKILLS", AIConfigTableBuilder.buildSkillsTable())
        val workflow = buildSectionHtml("WORKFLOW", AIConfigTableBuilder.buildWorkflowTable())
        val instructions = buildSectionHtml("INSTRUCTIONS", AIConfigTableBuilder.buildInstructionsTable())
        val rules = buildSectionHtml("RULES", AIConfigTableBuilder.buildRulesTable())
        val saveBtn = """<button id="btn-cfg-save" class="btn-vibrant" style="width:100%;padding:12px;font-size:12px;letter-spacing:1.5px;">SAVE</button>"""
        return "$header$skills$workflow$instructions$rules$saveBtn"
    }

    private fun buildHeaderHtml() = """
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
            <div style="font-size:15px;font-weight:700;">AI Personalization</div>
            <button id="btn-cfg-close" class="chat-close-btn">✕</button>
        </div>""".trimIndent()

    private fun buildSectionHtml(label: String, tableHtml: String) = """
        <div class="config-field"><label>$label</label>$tableHtml</div>""".trimIndent()
}
