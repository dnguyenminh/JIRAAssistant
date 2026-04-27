package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.TicketPageState
import kotlinx.browser.window
import kotlinx.serialization.json.Json

/**
 * Saves and restores TicketPageState to sessionStorage.
 * Enables state persistence when user navigates away and returns.
 * Requirements: 23.1, 23.2
 */
internal object TicketStateManager {

    private const val STORAGE_KEY = "ticket_intelligence_state"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Serialize TicketPageState to JSON and save to sessionStorage.
     */
    fun save(state: TicketPageState) {
        try {
            val serialized = json.encodeToString(
                TicketPageState.serializer(), state
            )
            window.sessionStorage.setItem(STORAGE_KEY, serialized)
        } catch (e: Exception) {
            console.log("[TicketStateManager] Save failed: ${e.message}")
        }
    }

    /**
     * Read from sessionStorage, deserialize, return null if not found or on parse failure.
     */
    fun restore(): TicketPageState? {
        return try {
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
                ?: return null
            json.decodeFromString(TicketPageState.serializer(), raw)
        } catch (e: Exception) {
            console.log("[TicketStateManager] Restore failed: ${e.message}")
            null
        }
    }

    /**
     * Remove persisted state from sessionStorage.
     */
    fun clear() {
        window.sessionStorage.removeItem(STORAGE_KEY)
    }
}
