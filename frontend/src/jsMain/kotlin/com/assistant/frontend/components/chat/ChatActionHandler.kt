package com.assistant.frontend.components.chat

import com.assistant.chat.ChatAction
import com.assistant.chat.ChatActionRequest
import com.assistant.chat.ChatActionResponse
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.services.NavigationContext
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Executes AI-suggested actions (navigate, changeConfig, triggerAnalysis).
 * Req 19.12, 19.13, 19.14, 19.15
 */
internal object ChatActionHandler {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    fun execute(
        action: ChatAction,
        messagesEl: HTMLElement?,
        scrollFn: () -> Unit,
        showErrorFn: (String) -> Unit
    ) {
        when {
            action.type == "navigate" -> handleNavigate(action, messagesEl, scrollFn)
            GraphActionHandler.canHandle(action) -> handleGraphAction(action, messagesEl, scrollFn)
            else -> handleServerAction(action, messagesEl, scrollFn, showErrorFn)
        }
    }

    private fun handleGraphAction(
        action: ChatAction, messagesEl: HTMLElement?, scrollFn: () -> Unit
    ) {
        val statusMessage = GraphActionHandler.execute(action)
        val confirmEl = ChatMessageRenderer.renderMessage("assistant", statusMessage)
        messagesEl?.appendChild(confirmEl)
        scrollFn()
    }

    private fun handleNavigate(
        action: ChatAction, messagesEl: HTMLElement?, scrollFn: () -> Unit
    ) {
        val screen = action.params["screen"] ?: ""
        if (screen.isNotBlank()) {
            storeNavigationContext(screen, action.params)
            window.location.hash = "#$screen"
        }
        val confirmEl = ChatMessageRenderer.renderMessage("assistant", "Đã điều hướng đến **${action.label}**.")
        messagesEl?.appendChild(confirmEl)
        scrollFn()
    }

    /**
     * Store extra params (excluding "screen") as navigation context
     * so the destination page can display relevant information.
     */
    private fun storeNavigationContext(
        screen: String, params: Map<String, String>
    ) {
        val contextParams = params.filterKeys { it != "screen" }
        if (contextParams.isNotEmpty()) {
            NavigationContext.store(screen, contextParams)
        }
    }

    private fun handleServerAction(
        action: ChatAction, messagesEl: HTMLElement?,
        scrollFn: () -> Unit, showErrorFn: (String) -> Unit
    ) {
        val request = ChatActionRequest(
            actionType = action.type, parameters = action.params
        )
        scope.launch {
            try {
                val response = ApiClient.post("/api/chat/execute-action", request)
                if (ApiClient.handleUnauthorized(response)) return@launch
                when (response.status) {
                    HttpStatusCode.Forbidden -> {
                        val msg = "Bạn không có quyền thực hiện thao tác này."
                        messagesEl?.appendChild(ChatMessageRenderer.renderMessage("assistant", msg))
                    }
                    HttpStatusCode.OK -> {
                        val body = response.bodyAsText()
                        val result = json.decodeFromString<ChatActionResponse>(body)
                        val confirmMsg = if (result.success) "✅ ${result.details}" else "❌ ${result.details}"
                        messagesEl?.appendChild(ChatMessageRenderer.renderMessage("assistant", confirmMsg))
                    }
                    else -> showErrorFn("Lỗi thực hiện hành động: ${response.bodyAsText()}")
                }
                scrollFn()
            } catch (e: Exception) {
                showErrorFn("Không thể thực hiện hành động: ${e.message}")
            }
        }
    }
}
