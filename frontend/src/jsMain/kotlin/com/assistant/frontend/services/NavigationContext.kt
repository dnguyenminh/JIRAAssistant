package com.assistant.frontend.services

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stores pending navigation context so destination pages can
 * display relevant information after a cross-page navigate action.
 *
 * Context is persisted in sessionStorage and consumed (cleared)
 * on first read — one-shot delivery.
 *
 * Requirements: 10.1, 10.2
 */
object NavigationContext {

    private const val STORAGE_KEY = "nav_context"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Store context before navigating to a new page.
     * @param screen target screen name (e.g. "ticket_intelligence")
     * @param params additional context (ticketKey, filter, etc.)
     */
    fun store(screen: String, params: Map<String, String>) {
        val ctx = PendingContext(screen = screen, params = params)
        val serialized = json.encodeToString(
            PendingContext.serializer(), ctx
        )
        window.sessionStorage.setItem(STORAGE_KEY, serialized)
    }

    /**
     * Consume (read + clear) pending context for the given screen.
     * Returns null if no context exists or if it targets a different screen.
     */
    fun consume(screen: String): Map<String, String>? {
        val raw = window.sessionStorage.getItem(STORAGE_KEY) ?: return null
        window.sessionStorage.removeItem(STORAGE_KEY)
        return try {
            val ctx = json.decodeFromString(PendingContext.serializer(), raw)
            if (ctx.screen == screen) ctx.params else null
        } catch (_: Exception) {
            null
        }
    }

    /** Peek without consuming — useful for checking if context exists. */
    fun peek(): PendingContext? {
        val raw = window.sessionStorage.getItem(STORAGE_KEY) ?: return null
        return try {
            json.decodeFromString(PendingContext.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Clear any pending context. */
    fun clear() {
        window.sessionStorage.removeItem(STORAGE_KEY)
    }

    @Serializable
    data class PendingContext(
        val screen: String,
        val params: Map<String, String> = emptyMap()
    )
}
