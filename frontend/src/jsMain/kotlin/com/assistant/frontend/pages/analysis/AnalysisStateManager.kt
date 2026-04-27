package com.assistant.frontend.pages.analysis

import com.assistant.frontend.models.ProjectAnalysisResponse
import kotlinx.browser.window
import kotlinx.serialization.json.Json

/** Saves/restores ProjectAnalysisResponse to sessionStorage. Requirements: 2.1, 2.4 */
internal object AnalysisStateManager {

    private const val STORAGE_KEY = "analysis_page_state"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    fun save(data: ProjectAnalysisResponse) {
        try {
            val serialized = json.encodeToString(ProjectAnalysisResponse.serializer(), data)
            window.sessionStorage.setItem(STORAGE_KEY, serialized)
        } catch (e: Exception) {
            console.log("[AnalysisStateManager] Save failed: ${e.message}")
        }
    }

    fun restore(): ProjectAnalysisResponse? {
        return try {
            val raw = window.sessionStorage.getItem(STORAGE_KEY) ?: return null
            json.decodeFromString(ProjectAnalysisResponse.serializer(), raw)
        } catch (e: Exception) {
            console.log("[AnalysisStateManager] Restore failed: ${e.message}")
            null
        }
    }

    fun clear() {
        window.sessionStorage.removeItem(STORAGE_KEY)
    }
}
