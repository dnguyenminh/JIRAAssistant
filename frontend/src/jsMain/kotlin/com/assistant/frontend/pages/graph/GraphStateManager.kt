package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphLayoutResponse
import kotlinx.browser.window
import kotlinx.serialization.json.Json

/** Saves/restores GraphLayoutResponse to sessionStorage. Requirements: 2.2, 2.5 */
internal object GraphStateManager {

    private const val STORAGE_KEY = "knowledge_graph_state"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    fun save(data: GraphLayoutResponse) {
        try {
            val serialized = json.encodeToString(GraphLayoutResponse.serializer(), data)
            window.sessionStorage.setItem(STORAGE_KEY, serialized)
        } catch (e: Exception) {
            console.log("[GraphStateManager] Save failed: ${e.message}")
        }
    }

    fun restore(): GraphLayoutResponse? {
        return try {
            val raw = window.sessionStorage.getItem(STORAGE_KEY) ?: return null
            json.decodeFromString(GraphLayoutResponse.serializer(), raw)
        } catch (e: Exception) {
            console.log("[GraphStateManager] Restore failed: ${e.message}")
            null
        }
    }

    fun clear() {
        window.sessionStorage.removeItem(STORAGE_KEY)
    }
}
