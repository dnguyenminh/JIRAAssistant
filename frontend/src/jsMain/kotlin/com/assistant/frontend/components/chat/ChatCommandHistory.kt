package com.assistant.frontend.components.chat

import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent

/**
 * Manages command history navigation with ↑/↓ keys. (Req 19.17a)
 */
internal class ChatCommandHistory {

    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private var savedInput = ""

    fun add(message: String) {
        history.add(message)
        historyIndex = history.size
    }

    fun clear() {
        history.clear()
        historyIndex = -1
        savedInput = ""
    }

    fun populateFromMessages(messages: List<String>) {
        history.clear()
        history.addAll(messages)
        historyIndex = history.size
    }

    fun handleKeyDown(
        ke: KeyboardEvent,
        input: HTMLTextAreaElement,
        sendFn: () -> Unit
    ) {
        when (ke.key) {
            "Enter" -> {
                if (!ke.shiftKey) { ke.preventDefault(); sendFn() }
            }
            "ArrowUp" -> {
                if (history.isEmpty()) return
                ke.preventDefault()
                if (historyIndex == history.size) savedInput = input.value
                if (historyIndex > 0) { historyIndex--; input.value = history[historyIndex] }
            }
            "ArrowDown" -> {
                if (history.isEmpty()) return
                ke.preventDefault()
                if (historyIndex < history.size - 1) {
                    historyIndex++; input.value = history[historyIndex]
                } else if (historyIndex == history.size - 1) {
                    historyIndex = history.size; input.value = savedInput
                }
            }
        }
    }
}
