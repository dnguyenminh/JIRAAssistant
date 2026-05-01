package com.assistant.server.document

import com.assistant.settings.SettingsRepository

/**
 * Helper for reading the `deep_collection_enabled` feature flag.
 *
 * The setting is stored as a string "true"/"false" in the app_settings
 * key-value store. Default is `true` when not explicitly set.
 *
 * No server restart required — the value is read from the DB on each call.
 *
 * Requirements: 12.1
 */
object DeepCollectionSettings {

    /** Key used in the app_settings table. */
    const val KEY = "deep_collection_enabled"

    /**
     * Check whether deep collection is enabled.
     *
     * @return true (default) unless explicitly set to "false".
     */
    suspend fun isEnabled(settingsRepository: SettingsRepository): Boolean {
        val value = settingsRepository.get(KEY)
        return value != "false"
    }
}
