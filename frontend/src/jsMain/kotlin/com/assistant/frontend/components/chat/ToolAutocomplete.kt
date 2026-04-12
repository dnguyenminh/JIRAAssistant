package com.assistant.frontend.components.chat

import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent

/**
 * Detects @ keypress in textarea and triggers tool autocomplete dropdown.
 * Requirements: 19.58
 */
object ToolAutocomplete {

    fun init(input: HTMLTextAreaElement) {
        input.addEventListener("input", {
            val value = input.value
            val cursorPos = input.selectionStart ?: value.length
            val textBeforeCursor = value.substring(0, cursorPos)
            val atIndex = textBeforeCursor.lastIndexOf('@')
            if (atIndex >= 0) {
                val query = textBeforeCursor.substring(atIndex + 1)
                if (query.isNotEmpty() && !query.contains(' ')) {
                    ToolPicker.filterAndShow(query)
                } else if (query.isEmpty()) {
                    ToolPicker.filterAndShow("")
                } else {
                    ToolPicker.hide()
                }
            } else {
                ToolPicker.hide()
            }
        })
    }
}
