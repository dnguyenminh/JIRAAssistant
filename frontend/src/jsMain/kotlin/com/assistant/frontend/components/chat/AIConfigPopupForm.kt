package com.assistant.frontend.components.chat

import com.assistant.chat.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent

/**
 * Popup modal form infrastructure + Skill/Workflow forms.
 * Requirements: AC 19.38a, AC 19.38b, AC 19.38d, AC 19.38e, AC 19.38h, AC 19.38i
 */
object AIConfigPopupForm {

    internal var currentOverlay: HTMLElement? = null

    fun showModal(title: String, bodyHtml: String, onSave: () -> Boolean) {
        closeModal()
        val overlay = document.createElement("div") as HTMLElement
        overlay.className = "config-modal-overlay"
        overlay.innerHTML = buildModalHtml(title, bodyHtml)
        document.body?.appendChild(overlay)
        currentOverlay = overlay
        bindModalEvents(overlay, onSave)
    }

    fun closeModal() {
        currentOverlay?.remove()
        currentOverlay = null
    }

    private fun buildModalHtml(title: String, body: String) = """<div class="config-modal">
        <div class="config-modal-header">
            <span>$title</span>
            <button class="config-modal-close chat-close-btn">✕</button>
        </div>
        <div class="config-modal-body">$body</div>
        <div class="config-modal-footer">
            <button class="btn-modal-cancel">Cancel</button>
            <button class="btn-modal-save btn-vibrant">Save</button>
        </div></div>"""

    private fun bindModalEvents(overlay: HTMLElement, onSave: () -> Boolean) {
        overlay.addEventListener("click", { e ->
            if ((e.target as? HTMLElement) === overlay) closeModal()
        })
        val close = overlay.querySelector(".config-modal-close")
        close?.addEventListener("click", { closeModal() })
        overlay.querySelector(".btn-modal-cancel")?.addEventListener("click", { closeModal() })
        overlay.querySelector(".btn-modal-save")?.addEventListener("click", { if (onSave()) closeModal() })
        window.addEventListener("keydown", { e ->
            if ((e as? KeyboardEvent)?.key == "Escape") closeModal()
        })
    }

    fun validateRequired(selector: String): Boolean {
        val ov = currentOverlay ?: return false
        val el = ov.querySelector(selector) as? HTMLElement ?: return false
        val value = getFieldValue(el)
        val parent = el.parentElement ?: return false
        parent.querySelector(".field-error-msg")?.remove()
        el.classList.remove("field-error")
        if (value.isBlank()) {
            el.classList.add("field-error")
            val msg = document.createElement("span") as HTMLElement
            msg.className = "field-error-msg"
            msg.textContent = "Trường này là bắt buộc"
            parent.appendChild(msg)
            return false
        }
        return true
    }

    internal fun getFieldValue(el: HTMLElement): String = when (el) {
        is HTMLInputElement -> el.value.trim()
        is HTMLTextAreaElement -> el.value.trim()
        is HTMLSelectElement -> el.value.trim()
        else -> ""
    }

    internal fun buildSelectHtml(id: String, opts: List<String>, selected: String): String {
        val optHtml = opts.joinToString("") {
            val sel = if (it == selected) " selected" else ""
            """<option value="$it"$sel>$it</option>"""
        }
        return """<select id="$id" class="modal-select">$optHtml</select>"""
    }

    // --- Skill Form (AC 19.38d) ---

    fun showSkillForm(existing: SkillEntry?, onResult: (SkillEntry) -> Unit) {
        val body = buildSkillBody(existing)
        showModal(if (existing != null) "Chỉnh sửa kỹ năng" else "Thêm kỹ năng", body) {
            saveSkillForm(onResult)
        }
    }

    private fun buildSkillBody(e: SkillEntry?): String {
        val name = e?.name ?: ""; val desc = e?.description ?: ""; val lvl = e?.level ?: "Intermediate"
        return """<div class="modal-field"><label>Tên kỹ năng *</label>
            <input type="text" id="mf-skill-name" class="modal-input" value="$name" placeholder="Tên kỹ năng"></div>
            <div class="modal-field"><label>Mức độ *</label>
            ${buildSelectHtml("mf-skill-level", listOf("Beginner","Intermediate","Expert"), lvl)}</div>
            <div class="modal-field"><label>Mô tả</label>
            <textarea id="mf-skill-desc" class="modal-textarea" placeholder="Mô tả chi tiết...">$desc</textarea></div>"""
    }

    private fun saveSkillForm(onResult: (SkillEntry) -> Unit): Boolean {
        if (!validateRequired("#mf-skill-name")) return false
        val ov = currentOverlay ?: return false
        val name = (ov.querySelector("#mf-skill-name") as HTMLInputElement).value.trim()
        val level = (ov.querySelector("#mf-skill-level") as HTMLSelectElement).value
        val desc = (ov.querySelector("#mf-skill-desc") as? HTMLTextAreaElement)?.value?.trim() ?: ""
        onResult(SkillEntry(name, level, desc)); return true
    }

    // --- Workflow Form (AC 19.38e) ---

    fun showWorkflowForm(existing: WorkflowEntry?, nextStep: Int, onResult: (WorkflowEntry) -> Unit) {
        val body = buildWorkflowBody(existing, nextStep)
        showModal(if (existing != null) "Chỉnh sửa quy trình" else "Thêm bước", body) {
            saveWorkflowForm(onResult)
        }
    }

    private fun buildWorkflowBody(e: WorkflowEntry?, nextStep: Int): String {
        val step = e?.step ?: nextStep; val name = e?.name ?: ""; val desc = e?.description ?: ""
        return """<div class="modal-field"><label>Bước *</label>
            <input type="number" id="mf-wf-step" class="modal-input" value="$step" min="1"></div>
            <div class="modal-field"><label>Tên quy trình *</label>
            <input type="text" id="mf-wf-name" class="modal-input" value="$name" placeholder="Tên quy trình"></div>
            <div class="modal-field"><label>Mô tả chi tiết *</label>
            <textarea id="mf-wf-desc" class="modal-textarea" placeholder="Mô tả chi tiết...">$desc</textarea></div>"""
    }

    private fun saveWorkflowForm(onResult: (WorkflowEntry) -> Unit): Boolean {
        val v1 = validateRequired("#mf-wf-name"); val v2 = validateRequired("#mf-wf-desc")
        if (!v1 || !v2) return false
        val ov = currentOverlay ?: return false
        val step = (ov.querySelector("#mf-wf-step") as HTMLInputElement).value.toIntOrNull() ?: 1
        val name = (ov.querySelector("#mf-wf-name") as HTMLInputElement).value.trim()
        val desc = (ov.querySelector("#mf-wf-desc") as HTMLTextAreaElement).value.trim()
        onResult(WorkflowEntry(step, name, desc)); return true
    }
}
