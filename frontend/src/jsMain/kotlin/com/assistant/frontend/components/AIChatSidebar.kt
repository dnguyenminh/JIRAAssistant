package com.assistant.frontend.components

import com.assistant.chat.*
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.chat.*
import com.assistant.frontend.models.ModelInfoResponse
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent

/**
 * AI Chat Sidebar controller — integrates all chat sub-components.
 * Requirements: 19.1–19.3, 19.5, 19.7, 19.8, 19.12–19.15, 19.17–19.19, 8.1–8.3
 */
object AIChatSidebar {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val cmdHistory = ChatCommandHistory()

    private var sidebarEl: HTMLElement? = null
    private var messagesEl: HTMLElement? = null
    private var inputEl: HTMLTextAreaElement? = null
    private var sendBtn: HTMLButtonElement? = null
    private var isOpen = false
    private var historyLoaded = false
    private var isSending = false

    fun init() {
        cacheElements()
        val wasOpen = window.sessionStorage.getItem("chat_sidebar_open") == "true"
        isOpen = wasOpen
        if (isOpen) {
            sidebarEl?.classList?.add("open")
            if (!historyLoaded) { loadInitialData(); historyLoaded = true }
        }
        bindCoreEvents()
        initSubComponents()
    }

    fun toggle() {
        val sidebar = sidebarEl ?: return
        isOpen = !isOpen
        window.sessionStorage.setItem("chat_sidebar_open", isOpen.toString())
        if (isOpen) {
            sidebar.classList.add("open")
            if (!historyLoaded) { loadInitialData(); historyLoaded = true }
            inputEl?.focus()
        } else sidebar.classList.remove("open")
    }

    fun loadHistory() {
        val convId = ConversationList.activeConversationId
        val url = if (!convId.isNullOrBlank()) "/api/chat/history?page=0&size=50&conversationId=$convId"
        else "/api/chat/history?page=0&size=50"
        scope.launch {
            try {
                val resp = ApiClient.get(url)
                if (resp.status == HttpStatusCode.OK) {
                    val hist = json.decodeFromString<ChatHistoryResponse>(resp.bodyAsText())
                    renderHistoryMessages(hist)
                }
            } catch (e: Exception) {
                console.log("[AIChatSidebar] Load history failed: ${e.message}")
            }
        }
    }

    fun sendMessage() {
        val input = inputEl ?: return
        val message = input.value.trim()
        if (message.isBlank() || isSending) return
        isSending = true; hideError()
        appendUserMessage(message)
        val request = buildChatRequest(message)
        scope.launch {
            try {
                val resp = ApiClient.post("/api/chat/send", request)
                hideTyping()
                if (ApiClient.handleUnauthorized(resp)) { isSending = false; return@launch }
                if (resp.status == HttpStatusCode.OK) handleSuccess(resp) else handleError(resp)
            } catch (e: Exception) {
                hideTyping(); showError("Cannot connect to AI. Please try again.")
            } finally { isSending = false; scrollToBottom() }
        }
    }

    /** Called by GraphFilterPanel.onFilterChange() to keep graph context fresh. */
    fun updateGraphContext() { ChatGraphContextBuilder.refresh() }

    fun showError(msg: String) {
        val banner = document.getElementById("chat-error-banner") as? HTMLElement ?: return
        banner.style.display = "block"; banner.textContent = msg
    }

    // -- Private helpers --

    private fun appendUserMessage(message: String) {
        messagesEl?.appendChild(ChatMessageRenderer.renderMessage("user", message))
        cmdHistory.add(message)
        inputEl?.let { it.value = "" }
        showTyping(); scrollToBottom()
    }

    private fun buildChatRequest(message: String): ChatRequest {
        val attachments = FileUploader.getPendingFiles().map {
            ChatAttachment(it.fileId, it.fileName, it.fileType, it.fileUrl)
        }
        FileUploader.clearPending()
        return ChatRequest(message, buildContext(), ConversationList.activeConversationId, attachments)
    }

    private suspend fun handleSuccess(resp: HttpResponse) {
        val chatResp = json.decodeFromString<ChatResponse>(resp.bodyAsText())
        messagesEl?.appendChild(ChatMessageRenderer.renderMessage("assistant", chatResp.reply))
        if (chatResp.actions.isNotEmpty()) messagesEl?.appendChild(renderActions(chatResp.actions))
        ContextIndicator.update(chatResp.contextUsage)
    }

    private suspend fun handleError(resp: HttpResponse) {
        val body = resp.bodyAsText()
        if (body.contains("provider", true) || resp.status.value == 503) {
            showError("No AI provider available. Configure in Integrations.")
        } else showError("Error: $body")
    }

    private fun renderActions(actions: List<ChatAction>): HTMLElement {
        val container = document.createElement("div") as HTMLElement
        container.className = "chat-actions"
        for (action in actions) {
            val btn = document.createElement("button") as HTMLButtonElement
            btn.className = "chat-action-btn"; btn.textContent = action.label
            btn.addEventListener("click", {
                ChatActionHandler.execute(action, messagesEl, ::scrollToBottom, ::showError)
            })
            container.appendChild(btn)
        }
        return container
    }

    private fun hideError() {
        (document.getElementById("chat-error-banner") as? HTMLElement)?.let {
            it.style.display = "none"; it.textContent = ""
        }
    }

    private fun showTyping() { document.getElementById("typing-indicator")?.asDynamic()?.style?.display = "flex" }
    private fun hideTyping() { document.getElementById("typing-indicator")?.asDynamic()?.style?.display = "none" }
    private fun scrollToBottom() { messagesEl?.let { it.scrollTop = it.scrollHeight.toDouble() } }

    private fun buildContext(): ChatContext {
        val screen = window.location.hash.removePrefix("#").ifBlank { "dashboard" }
        return ChatContext(
            projectKey = ApiClient.getProjectKey() ?: "",
            currentScreen = screen,
            userRole = ApiClient.getUserRole()?.name ?: "READER",
            userId = ApiClient.getUserEmail() ?: "",
            graphContext = ChatGraphContextBuilder.current()
        )
    }

    private fun cacheElements() {
        sidebarEl = document.getElementById("ai-chat-sidebar") as? HTMLElement
        messagesEl = document.getElementById("chat-messages") as? HTMLElement
        inputEl = document.getElementById("chat-input") as? HTMLTextAreaElement
        sendBtn = document.getElementById("btn-send-chat") as? HTMLButtonElement
    }

    private fun bindCoreEvents() {
        document.getElementById("btn-close-chat")?.addEventListener("click", { toggle() })
        sendBtn?.addEventListener("click", { sendMessage() })
        inputEl?.addEventListener("keydown", { e ->
            cmdHistory.handleKeyDown(e.unsafeCast<KeyboardEvent>(), inputEl!!, ::sendMessage)
        })
        document.getElementById("btn-new-chat")?.addEventListener("click", { ConversationList.createNew() })
        document.getElementById("btn-chat-config")?.addEventListener("click", {
            sidebarEl?.let { AIConfigPanel.open(it) }
        })
        document.getElementById("btn-attach-file")?.addEventListener("click", { FileUploader.openFilePicker() })
        document.getElementById("btn-tools-picker")?.addEventListener("click", { ToolPicker.toggle() })
    }

    private fun initSubComponents() {
        val input = inputEl ?: return
        val voiceBtn = document.getElementById("btn-voice-input") as? HTMLButtonElement
        if (voiceBtn != null) VoiceInput.init(input, voiceBtn)
        ClipboardHandler.init(input)
        ToolPicker.init(input)
        ToolAutocomplete.init(input)
        ConversationList.onSwitch = { loadHistory() }
    }

    private fun loadInitialData() {
        ConversationList.load()
        loadHistory()
        loadModelInfo()
        AIConfigPanel.updateBadge()
    }

    private fun loadModelInfo() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/chat/model-info")
                if (resp.status == HttpStatusCode.OK) {
                    val info = json.decodeFromString<ModelInfoResponse>(resp.bodyAsText())
                    document.getElementById("model-name-badge")?.textContent = info.modelName
                    if (info.supportsTools) {
                        document.getElementById("btn-tools-picker")?.asDynamic()?.style?.display = "flex"
                    }
                }
            } catch (_: Exception) { /* model info optional */ }
        }
    }

    private fun renderHistoryMessages(hist: ChatHistoryResponse) {
        messagesEl?.innerHTML = ""
        val userMsgs = mutableListOf<String>()
        for (msg in hist.messages) {
            messagesEl?.appendChild(ChatMessageRenderer.renderMessage(msg.role, msg.message))
            if (msg.role == "user") userMsgs.add(msg.message)
        }
        cmdHistory.populateFromMessages(userMsgs)
        scrollToBottom()
    }
}
