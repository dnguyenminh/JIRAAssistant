package com.assistant.frontend.components.chat

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.xhr.FormData

/**
 * File picker, upload, and preview for chat attachments.
 * Requirements: 19.31, 19.32
 */
object FileUploader {

    private val scope = MainScope()
    private val pendingFiles = mutableListOf<UploadedFile>()
    private const val ACCEPT = ".doc,.docx,.xls,.xlsx,.pdf,.png,.jpg,.jpeg,.gif,.webp"

    data class UploadedFile(val fileId: String, val fileName: String, val fileType: String, val fileUrl: String)

    fun openFilePicker() {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ACCEPT
        input.addEventListener("change", {
            val file = input.files?.item(0) ?: return@addEventListener
            uploadFile(file)
        })
        input.click()
    }

    fun uploadFile(file: File) {
        scope.launch {
            try {
                val formData = FormData()
                formData.append("file", file)
                val resp = window.fetch(
                    "/api/chat/upload",
                    js("{method:'POST',body:formData,headers:{'Authorization':'Bearer '+sessionStorage.getItem('jwt_token')}}")
                ).asDynamic().then { r: dynamic -> r.json() }.asDynamic()
                // Handle async result
                resp.then { data: dynamic ->
                    val uploaded = UploadedFile(
                        fileId = data.fileId as? String ?: "",
                        fileName = data.fileName as? String ?: file.name,
                        fileType = data.fileType as? String ?: "",
                        fileUrl = data.fileUrl as? String ?: ""
                    )
                    pendingFiles.add(uploaded)
                    renderPreview()
                }
            } catch (e: Exception) {
                console.log("[FileUploader] Upload failed: ${e.message}")
            }
        }
    }

    fun getPendingFiles(): List<UploadedFile> = pendingFiles.toList()
    fun clearPending() { pendingFiles.clear(); hidePreview() }

    private fun renderPreview() {
        val container = document.getElementById("chat-file-preview") as? HTMLElement ?: return
        container.style.display = if (pendingFiles.isEmpty()) "none" else "flex"
        container.innerHTML = ""
        for (file in pendingFiles) {
            val thumb = document.createElement("div") as HTMLElement
            thumb.className = "file-thumbnail"
            val icon = if (file.fileType.startsWith("image")) "🖼️" else "📄"
            val nameSpan = document.createElement("span") as HTMLElement
            nameSpan.textContent = "$icon ${file.fileName}"
            val removeBtn = document.createElement("button") as HTMLElement
            removeBtn.className = "file-remove"
            removeBtn.textContent = "✕"
            removeBtn.addEventListener("click", {
                pendingFiles.removeAll { it.fileId == file.fileId }
                renderPreview()
            })
            thumb.appendChild(nameSpan)
            thumb.appendChild(removeBtn)
            container.appendChild(thumb)
        }
    }

    private fun hidePreview() {
        (document.getElementById("chat-file-preview") as? HTMLElement)?.style?.display = "none"
    }
}
