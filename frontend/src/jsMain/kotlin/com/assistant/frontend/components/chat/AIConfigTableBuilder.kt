package com.assistant.frontend.components.chat

import com.assistant.chat.*
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTableRowElement

/**
 * HTML table builder helpers for AI Personalization config panel.
 * Builds editable tables for Skills, Workflow, Instructions, Rules.
 * Requirements: 19.38, 19.38a, 19.38b
 */
object AIConfigTableBuilder {

    fun buildSkillsTable(): String = buildTable(
        id = "tbl-skills",
        headers = listOf("Tên kỹ năng", "Mức độ", "Mô tả", "🗑️"),
        addLabel = "➕ Thêm kỹ năng"
    )

    fun buildWorkflowTable(): String = buildTable(
        id = "tbl-workflow",
        headers = listOf("Bước", "Tên quy trình", "Mô tả", "🗑️"),
        addLabel = "➕ Thêm bước"
    )

    fun buildInstructionsTable(): String = buildTable(
        id = "tbl-instructions",
        headers = listOf("Hướng dẫn", "Độ ưu tiên", "🗑️"),
        addLabel = "➕ Thêm hướng dẫn"
    )

    fun buildRulesTable(): String = buildTable(
        id = "tbl-rules",
        headers = listOf("Quy tắc", "Loại", "🗑️"),
        addLabel = "➕ Thêm quy tắc"
    )

    private fun buildTable(id: String, headers: List<String>, addLabel: String): String {
        val ths = headers.joinToString("") { "<th>$it</th>" }
        return """<table class="config-table" id="$id"><thead><tr>$ths</tr></thead>
            <tbody></tbody></table>
            <button class="btn-add-row" data-table="$id">$addLabel</button>"""
    }


    fun addSkillRow(tableId: String, entry: SkillEntry? = null) {
        val tbody = getTableBody(tableId) ?: return
        val row = document.createElement("tr") as HTMLTableRowElement
        row.innerHTML = buildSkillRowHtml(entry)
        tbody.appendChild(row)
        bindDeleteButton(row)
    }

    fun addWorkflowRow(tableId: String, entry: WorkflowEntry? = null) {
        val tbody = getTableBody(tableId) ?: return
        val step = tbody.children.length + 1
        val row = document.createElement("tr") as HTMLTableRowElement
        row.innerHTML = buildWorkflowRowHtml(step, entry)
        tbody.appendChild(row)
        bindDeleteButton(row)
    }

    fun addInstructionRow(tableId: String, entry: InstructionEntry? = null) {
        val tbody = getTableBody(tableId) ?: return
        val row = document.createElement("tr") as HTMLTableRowElement
        row.innerHTML = buildInstructionRowHtml(entry)
        tbody.appendChild(row)
        bindDeleteButton(row)
    }

    fun addRuleRow(tableId: String, entry: RuleEntry? = null) {
        val tbody = getTableBody(tableId) ?: return
        val row = document.createElement("tr") as HTMLTableRowElement
        row.innerHTML = buildRuleRowHtml(entry)
        tbody.appendChild(row)
        bindDeleteButton(row)
    }

    private fun buildSkillRowHtml(e: SkillEntry?): String {
        val name = e?.name ?: ""
        val desc = e?.description ?: ""
        val lvl = e?.level ?: "Intermediate"
        return """<td><input type="text" class="cfg-input" value="$name" placeholder="Tên kỹ năng"></td>
            <td>${buildLevelSelect(lvl)}</td>
            <td><input type="text" class="cfg-input" value="$desc" placeholder="Mô tả"></td>
            <td><button class="btn-delete-row">🗑️</button></td>"""
    }

    private fun buildWorkflowRowHtml(step: Int, e: WorkflowEntry?): String {
        val name = e?.name ?: ""
        val desc = e?.description ?: ""
        return """<td class="step-number">$step</td>
            <td><input type="text" class="cfg-input" value="$name" placeholder="Tên quy trình"></td>
            <td><input type="text" class="cfg-input" value="$desc" placeholder="Mô tả"></td>
            <td><button class="btn-delete-row">🗑️</button></td>"""
    }

    private fun buildInstructionRowHtml(e: InstructionEntry?): String {
        val instr = e?.instruction ?: ""
        val pri = e?.priority ?: "Trung bình"
        return """<td><input type="text" class="cfg-input" value="$instr" placeholder="Hướng dẫn cho AI"></td>
            <td>${buildPrioritySelect(pri)}</td>
            <td><button class="btn-delete-row">🗑️</button></td>"""
    }

    private fun buildRuleRowHtml(e: RuleEntry?): String {
        val rule = e?.rule ?: ""
        val type = e?.type ?: "Bắt buộc"
        return """<td><input type="text" class="cfg-input" value="$rule" placeholder="Quy tắc"></td>
            <td>${buildRuleTypeSelect(type)}</td>
            <td><button class="btn-delete-row">🗑️</button></td>"""
    }

    private fun buildLevelSelect(selected: String): String {
        val opts = listOf("Beginner", "Intermediate", "Expert")
        return buildSelect(opts, selected, "cfg-select")
    }

    private fun buildPrioritySelect(selected: String): String {
        val opts = listOf("Cao", "Trung bình", "Thấp")
        return buildSelect(opts, selected, "cfg-select")
    }

    private fun buildRuleTypeSelect(selected: String): String {
        val opts = listOf("Cấm", "Bắt buộc", "Khuyến nghị")
        return buildSelect(opts, selected, "cfg-select")
    }

    private fun buildSelect(options: List<String>, selected: String, cls: String): String {
        val optHtml = options.joinToString("") {
            val sel = if (it == selected) " selected" else ""
            """<option value="$it"$sel>$it</option>"""
        }
        return """<select class="$cls">$optHtml</select>"""
    }

    private fun getTableBody(tableId: String): HTMLElement? {
        val table = document.getElementById(tableId) ?: return null
        return table.querySelector("tbody") as? HTMLElement
    }

    fun bindDeleteButton(row: HTMLTableRowElement) {
        val btn = row.querySelector(".btn-delete-row") ?: return
        btn.addEventListener("click", {
            if (kotlinx.browser.window.confirm("Xóa dòng này?")) row.remove()
        })
    }

    // --- Data collection from table rows ---

    fun collectSkills(tableId: String): List<SkillEntry> {
        val tbody = getTableBody(tableId) ?: return emptyList()
        return (0 until tbody.children.length).mapNotNull { i ->
            val row = tbody.children.item(i) as? HTMLTableRowElement ?: return@mapNotNull null
            val inputs = row.querySelectorAll("input")
            val select = row.querySelector("select") as? HTMLSelectElement
            val name = (inputs.item(0) as? HTMLInputElement)?.value?.trim() ?: ""
            if (name.isBlank()) return@mapNotNull null
            val desc = (inputs.item(1) as? HTMLInputElement)?.value?.trim() ?: ""
            SkillEntry(name, select?.value ?: "Intermediate", desc)
        }
    }

    fun collectWorkflow(tableId: String): List<WorkflowEntry> {
        val tbody = getTableBody(tableId) ?: return emptyList()
        return (0 until tbody.children.length).mapNotNull { i ->
            val row = tbody.children.item(i) as? HTMLTableRowElement ?: return@mapNotNull null
            val inputs = row.querySelectorAll("input")
            val name = (inputs.item(0) as? HTMLInputElement)?.value?.trim() ?: ""
            if (name.isBlank()) return@mapNotNull null
            val desc = (inputs.item(1) as? HTMLInputElement)?.value?.trim() ?: ""
            WorkflowEntry(step = i + 1, name = name, description = desc)
        }
    }

    fun collectInstructions(tableId: String): List<InstructionEntry> {
        val tbody = getTableBody(tableId) ?: return emptyList()
        return (0 until tbody.children.length).mapNotNull { i ->
            val row = tbody.children.item(i) as? HTMLTableRowElement ?: return@mapNotNull null
            val input = row.querySelector("input") as? HTMLInputElement
            val select = row.querySelector("select") as? HTMLSelectElement
            val instr = input?.value?.trim() ?: ""
            if (instr.isBlank()) return@mapNotNull null
            InstructionEntry(instr, select?.value ?: "Trung bình")
        }
    }

    fun collectRules(tableId: String): List<RuleEntry> {
        val tbody = getTableBody(tableId) ?: return emptyList()
        return (0 until tbody.children.length).mapNotNull { i ->
            val row = tbody.children.item(i) as? HTMLTableRowElement ?: return@mapNotNull null
            val input = row.querySelector("input") as? HTMLInputElement
            val select = row.querySelector("select") as? HTMLSelectElement
            val rule = input?.value?.trim() ?: ""
            if (rule.isBlank()) return@mapNotNull null
            RuleEntry(rule, select?.value ?: "Bắt buộc")
        }
    }
}
