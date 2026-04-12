package com.assistant.frontend.components.chat

import com.assistant.chat.ChatConversation
import com.assistant.frontend.api.ApiClient
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Manages conversation list UI — load, render, switch, rename, delete.
 * Requirements: 19.44, 19.46, 19.50, 19.51, 19.52
 */
object ConversationList {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var conversations = mutableListOf<ChatConversation>()
    var activeConversationId: String? = null
    var onSwitch: ((String) -> Unit)? = null

    fun load() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/chat/conversations")
                if (resp.status == HttpStatusCode.OK) {
                    val body = resp.bodyAsText()
                    conversations.clear()
                    conversations.addAll(json.decodeFromString<List<ChatConversation>>(body))
                    render()
                }
            } catch (e: Exception) {
                console.log("[ConversationList] Load failed: ${e.message}")
            }
        }
    }

    fun createNew() {
        scope.launch {
            try {
                val resp = ApiClient.post("/api/chat/conversations", mapOf<String, String>())
                if (resp.status == HttpStatusCode.Created) {
                    val conv = json.decodeFromString<ChatConversation>(resp.bodyAsText())
                    conversations.add(0, conv)
                    activeConversationId = conv.id
                    render()
                    onSwitch?.invoke(conv.id)
                }
            } catch (e: Exception) {
                console.log("[ConversationList] Create failed: ${e.message}")
            }
        }
    }

    fun render() {
        val container = document.getElementById("conversation-items") ?: return
        container.innerHTML = ""
        for (conv in conversations) {
            container.appendChild(createConvItem(conv))
        }
    }

    private fun createConvItem(conv: ChatConversation): HTMLElement {
        val item = document.createElement("div") as HTMLElement
        item.className = "conversation-item${if (conv.id == activeConversationId) " active" else ""}"
        val title = document.createElement("span") as HTMLElement
        title.textContent = conv.title
        title.style.cssText = "flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
        title.addEventListener("dblclick", { promptRename(conv.id) })
        val delBtn = document.createElement("button") as HTMLElement
        delBtn.className = "conv-delete-btn"
        delBtn.textContent = "🗑️"
        delBtn.addEventListener("click", { e -> e.stopPropagation(); confirmDelete(conv.id) })
        item.appendChild(title)
        item.appendChild(delBtn)
        item.addEventListener("click", { switchTo(conv.id) })
        return item
    }

    private fun switchTo(id: String) {
        activeConversationId = id
        render()
        onSwitch?.invoke(id)
    }

    private fun promptRename(id: String) {
        val newTitle = window.prompt("Rename conversation:") ?: return
        if (newTitle.isBlank()) return
        scope.launch {
            try {
                ApiClient.put("/api/chat/conversations/$id", mapOf("title" to newTitle))
                val idx = conversations.indexOfFirst { it.id == id }
                if (idx >= 0) conversations[idx] = conversations[idx].copy(title = newTitle)
                render()
            } catch (e: Exception) {
                console.log("[ConversationList] Rename failed: ${e.message}")
            }
        }
    }

    private fun confirmDelete(id: String) {
        if (!window.confirm("Delete this conversation?")) return
        scope.launch {
            try {
                ApiClient.delete("/api/chat/conversations/$id")
                conversations.removeAll { it.id == id }
                if (activeConversationId == id) activeConversationId = conversations.firstOrNull()?.id
                render()
                activeConversationId?.let { onSwitch?.invoke(it) }
            } catch (e: Exception) {
                console.log("[ConversationList] Delete failed: ${e.message}")
            }
        }
    }
}
