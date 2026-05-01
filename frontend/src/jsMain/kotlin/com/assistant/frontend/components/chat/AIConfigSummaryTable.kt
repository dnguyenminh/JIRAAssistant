package com.assistant.frontend.components.chat

import com.assistant.chat.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTableRowElement

/**
 * Summary table builder for AI Config panel — read-only display.
 * Click row → popup edit. ➕ → popup add. 🗑️ → confirm delete.
 * Requirements: AC 19.38, AC 19.38a, AC 19.38b, AC 19.38c
 */
object AIConfigSummaryTable {

    var skills = mutableListOf<SkillEntry>()
    var workflow = mutableListOf<WorkflowEntry>()
    var instructions = mutableListOf<InstructionEntry>()
    var rules = mutableListOf<RuleEntry>()

    fun clearAll() { skills.clear(); workflow.clear(); instructions.clear(); rules.clear() }

    // --- Skills ---

    fun renderSkillsTable(id: String) {
        val c = document.getElementById(id) ?: return; c.innerHTML = ""
        val table = buildTable(listOf("Tên kỹ năng", "Mức độ", "🗑️"))
        val tbody = table.querySelector("tbody") as HTMLElement
        skills.forEachIndexed { i, e ->
            val row = createRow(e.name, e.level)
            bindRowClick(row, i) { AIConfigPopupForm.showSkillForm(skills[it]) { s -> skills[it] = s; renderSkillsTable(id) } }
            bindDelete(row, skills, i, id)
            tbody.appendChild(row)
        }
        c.appendChild(table)
        c.appendChild(addBtn("➕ Thêm kỹ năng") {
            AIConfigPopupForm.showSkillForm(null) { s -> skills.add(s); renderSkillsTable(id) }
        })
    }

    // --- Workflow ---

    fun renderWorkflowTable(id: String) {
        val c = document.getElementById(id) ?: return; c.innerHTML = ""
        val table = buildTable(listOf("Bước", "Tên quy trình", "🗑️"))
        val tbody = table.querySelector("tbody") as HTMLElement
        workflow.forEachIndexed { i, e ->
            val row = createRow("${e.step}", e.name, firstCls = "step-number")
            bindRowClick(row, i) { idx ->
                val ns = (workflow.maxOfOrNull { it.step } ?: 0) + 1
                AIConfigPopupForm.showWorkflowForm(workflow[idx], ns) { w -> workflow[idx] = w; renderWorkflowTable(id) }
            }
            bindDelete(row, workflow, i, id)
            tbody.appendChild(row)
        }
        c.appendChild(table)
        c.appendChild(addBtn("➕ Thêm bước") {
            val ns = (workflow.maxOfOrNull { it.step } ?: 0) + 1
            AIConfigPopupForm.showWorkflowForm(null, ns) { w -> workflow.add(w); renderWorkflowTable(id) }
        })
    }

    // --- Instructions ---

    fun renderInstructionsTable(id: String) {
        val c = document.getElementById(id) ?: return; c.innerHTML = ""
        val table = buildTable(listOf("Hướng dẫn", "Độ ưu tiên", "🗑️"))
        val tbody = table.querySelector("tbody") as HTMLElement
        instructions.forEachIndexed { i, e ->
            val row = createRow(truncate(e.instruction, 40), e.priority)
            bindRowClick(row, i) {
                AIConfigPopupFormExtra.showInstructionForm(instructions[it]) { ins -> instructions[it] = ins; renderInstructionsTable(id) }
            }
            bindDelete(row, instructions, i, id)
            tbody.appendChild(row)
        }
        c.appendChild(table)
        c.appendChild(addBtn("➕ Thêm hướng dẫn") {
            AIConfigPopupFormExtra.showInstructionForm(null) { ins -> instructions.add(ins); renderInstructionsTable(id) }
        })
    }

    // --- Rules ---

    fun renderRulesTable(id: String) {
        val c = document.getElementById(id) ?: return; c.innerHTML = ""
        val table = buildTable(listOf("Quy tắc", "Loại", "🗑️"))
        val tbody = table.querySelector("tbody") as HTMLElement
        rules.forEachIndexed { i, e ->
            val row = createRow(truncate(e.rule, 40), e.type)
            bindRowClick(row, i) {
                AIConfigPopupFormExtra.showRuleForm(rules[it]) { r -> rules[it] = r; renderRulesTable(id) }
            }
            bindDelete(row, rules, i, id)
            tbody.appendChild(row)
        }
        c.appendChild(table)
        c.appendChild(addBtn("➕ Thêm quy tắc") {
            AIConfigPopupFormExtra.showRuleForm(null) { r -> rules.add(r); renderRulesTable(id) }
        })
    }

    // --- Shared helpers ---

    private fun buildTable(headers: List<String>): HTMLElement {
        val t = document.createElement("table") as HTMLElement
        t.className = "config-table"
        t.innerHTML = "<thead><tr>${headers.joinToString("") { "<th>$it</th>" }}</tr></thead><tbody></tbody>"
        return t
    }

    private fun createRow(col1: String, col2: String, firstCls: String? = null): HTMLTableRowElement {
        val row = document.createElement("tr") as HTMLTableRowElement
        row.className = "summary-row"
        val td1 = document.createElement("td") as HTMLElement
        td1.textContent = col1; if (firstCls != null) td1.className = firstCls
        val td2 = document.createElement("td") as HTMLElement; td2.textContent = col2
        val td3 = document.createElement("td") as HTMLElement
        val btn = document.createElement("button") as HTMLElement
        btn.className = "btn-delete-row"; btn.textContent = "🗑️"
        td3.appendChild(btn)
        row.appendChild(td1); row.appendChild(td2); row.appendChild(td3)
        return row
    }

    private fun bindRowClick(row: HTMLTableRowElement, index: Int, onEdit: (Int) -> Unit) {
        row.addEventListener("click", { e ->
            if ((e.target as? HTMLElement)?.classList?.contains("btn-delete-row") != true) onEdit(index)
        })
    }

    private fun <T> bindDelete(row: HTMLTableRowElement, list: MutableList<T>, index: Int, containerId: String) {
        row.querySelector(".btn-delete-row")?.addEventListener("click", { e ->
            e.stopPropagation()
            if (window.confirm("Xóa dòng này?")) { list.removeAt(index); reRender(containerId) }
        })
    }

    private fun reRender(id: String) = when (id) {
        "tbl-skills-container" -> renderSkillsTable(id)
        "tbl-workflow-container" -> renderWorkflowTable(id)
        "tbl-instructions-container" -> renderInstructionsTable(id)
        "tbl-rules-container" -> renderRulesTable(id)
        else -> {}
    }

    private fun addBtn(label: String, onClick: () -> Unit): HTMLElement {
        val b = document.createElement("button") as HTMLElement
        b.className = "btn-add-row"; b.textContent = label
        b.addEventListener("click", { onClick() }); return b
    }

    private fun truncate(text: String, max: Int) = if (text.length > max) text.take(max) + "…" else text
}
