package com.assistant.frontend.components.chat

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Renders chat message bubbles with basic markdown parsing.
 */
internal object ChatMessageRenderer {

    fun renderMessage(role: String, content: String): HTMLElement {
        val wrapper = document.createElement("div") as HTMLElement
        wrapper.className = "chat-message $role"
        val bubble = document.createElement("div") as HTMLElement
        bubble.className = "chat-bubble"
        val displayText = if (role == "assistant") extractReplyText(content) else content
        bubble.innerHTML = parseMarkdown(displayText)
        wrapper.appendChild(bubble)
        return wrapper
    }

    /** If content looks like JSON with a "reply" field, extract just the reply text. */
    private fun extractReplyText(content: String): String {
        val trimmed = content.trim()
        // Check if content is JSON or contains JSON in code fence
        val jsonStr = if (trimmed.startsWith("{")) trimmed
            else extractJsonFromCodeFence(trimmed) ?: return content
        return try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr)
            val reply = (obj as? kotlinx.serialization.json.JsonObject)
                ?.get("reply")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            reply ?: content
        } catch (_: Exception) { content }
    }

    private fun extractJsonFromCodeFence(text: String): String? {
        val start = text.indexOf("```")
        if (start < 0) return null
        val afterFence = text.substring(start + 3)
        val nl = afterFence.indexOf('\n')
        if (nl < 0) return null
        val body = afterFence.substring(nl + 1)
        val end = body.lastIndexOf("```")
        return if (end >= 0) body.substring(0, end).trim() else null
    }

    fun parseMarkdown(text: String): String {
        var result = escapeHtml(text)
        result = replaceCodeBlocks(result)
        result = replaceInlineCode(result)
        result = replaceBold(result)
        result = replaceItalic(result)
        result = replaceListItems(result)
        result = result.replace("\n", "<br>")
        return result
    }

    private fun replaceCodeBlocks(text: String): String =
        text.replace(Regex("```([\\s\\S]*?)```")) { match ->
            "<pre class=\"chat-code-block\"><code>${match.groupValues[1].trim()}</code></pre>"
        }

    private fun replaceInlineCode(text: String): String =
        text.replace(Regex("`([^`]+)`")) { match ->
            "<code class=\"chat-inline-code\">${match.groupValues[1]}</code>"
        }

    private fun replaceBold(text: String): String =
        text.replace(Regex("\\*\\*(.+?)\\*\\*")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }

    private fun replaceItalic(text: String): String =
        text.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

    private fun replaceListItems(text: String): String {
        var result = text.replace(Regex("^- (.+)", RegexOption.MULTILINE)) { match ->
            "<li>${match.groupValues[1]}</li>"
        }
        result = result.replace(Regex("(<li>.*?</li>\\s*)+")) { match ->
            "<ul>${match.value}</ul>"
        }
        return result
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
