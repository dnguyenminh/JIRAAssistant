package com.assistant.frontend.components.chat

import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

/**
 * Clipboard paste handler — detects images and triggers upload.
 * Requirements: 19.35
 */
object ClipboardHandler {

    fun init(textarea: HTMLTextAreaElement) {
        textarea.addEventListener("paste", { e -> handlePaste(e) })
    }

    private fun handlePaste(event: Event) {
        val clipboardEvent = event.asDynamic()
        val items = clipboardEvent.clipboardData?.items ?: return
        val length = items.length as? Int ?: return
        for (i in 0 until length) {
            val item = items[i]
            val type = item.type as? String ?: continue
            if (type.startsWith("image/")) {
                event.preventDefault()
                val blob = item.getAsFile()
                if (blob != null) {
                    FileUploader.uploadFile(blob.unsafeCast<org.w3c.files.File>())
                }
                return
            }
        }
    }
}
