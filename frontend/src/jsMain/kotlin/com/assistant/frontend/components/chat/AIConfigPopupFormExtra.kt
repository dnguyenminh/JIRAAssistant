package com.assistant.frontend.components.chat

import com.assistant.chat.InstructionEntry
import com.assistant.chat.RuleEntry
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * Instruction and Rule popup forms — extension of AIConfigPopupForm.
 * Requirements: AC 19.38f, AC 19.38g
 */
object AIConfigPopupFormExtra {

    // --- Instruction Form (AC 19.38f) ---

    fun showInstructionForm(existing: InstructionEntry?, onResult: (InstructionEntry) -> Unit) {
        val body = buildInstructionBody(existing)
        val title = if (existing != null) "Chỉnh sửa hướng dẫn" else "Thêm hướng dẫn"
        AIConfigPopupForm.showModal(title, body) { saveInstructionForm(onResult) }
    }

    private fun buildInstructionBody(e: InstructionEntry?): String {
        val instr = e?.instruction ?: ""
        val pri = e?.priority ?: "Trung bình"
        return """<div class="modal-field"><label>Hướng dẫn *</label>
            <textarea id="mf-instr-text" class="modal-textarea" placeholder="Hướng dẫn cho AI...">$instr</textarea></div>
            <div class="modal-field"><label>Độ ưu tiên *</label>
            ${AIConfigPopupForm.buildSelectHtml("mf-instr-pri", listOf("Cao","Trung bình","Thấp"), pri)}</div>"""
    }

    private fun saveInstructionForm(onResult: (InstructionEntry) -> Unit): Boolean {
        if (!AIConfigPopupForm.validateRequired("#mf-instr-text")) return false
        val ov = AIConfigPopupForm.currentOverlay ?: return false
        val instr = (ov.querySelector("#mf-instr-text") as HTMLTextAreaElement).value.trim()
        val pri = (ov.querySelector("#mf-instr-pri") as HTMLSelectElement).value
        onResult(InstructionEntry(instr, pri))
        return true
    }

    // --- Rule Form (AC 19.38g) ---

    fun showRuleForm(existing: RuleEntry?, onResult: (RuleEntry) -> Unit) {
        val body = buildRuleBody(existing)
        val title = if (existing != null) "Chỉnh sửa quy tắc" else "Thêm quy tắc"
        AIConfigPopupForm.showModal(title, body) { saveRuleForm(onResult) }
    }

    private fun buildRuleBody(e: RuleEntry?): String {
        val rule = e?.rule ?: ""
        val type = e?.type ?: "Bắt buộc"
        return """<div class="modal-field"><label>Quy tắc *</label>
            <textarea id="mf-rule-text" class="modal-textarea" placeholder="Quy tắc cho AI...">$rule</textarea></div>
            <div class="modal-field"><label>Loại *</label>
            ${AIConfigPopupForm.buildSelectHtml("mf-rule-type", listOf("Cấm","Bắt buộc","Khuyến nghị"), type)}</div>"""
    }

    private fun saveRuleForm(onResult: (RuleEntry) -> Unit): Boolean {
        if (!AIConfigPopupForm.validateRequired("#mf-rule-text")) return false
        val ov = AIConfigPopupForm.currentOverlay ?: return false
        val rule = (ov.querySelector("#mf-rule-text") as HTMLTextAreaElement).value.trim()
        val type = (ov.querySelector("#mf-rule-type") as HTMLSelectElement).value
        onResult(RuleEntry(rule, type))
        return true
    }
}
