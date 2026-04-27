package com.assistant.settings

import kotlinx.serialization.Serializable

/**
 * Repository interface for application settings persistence.
 * Interface lives in commonMain; JVM implementation uses SQLDelight.
 */
interface SettingsRepository {
    suspend fun getAll(): Map<String, String>
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun putAll(settings: Map<String, String>)
}

/**
 * Full application settings used for input (PUT requests).
 */
@Serializable
data class AppSettings(
    val jiraHost: String? = null,
    val aiProviderUrl: String? = null,
    val jwtSecret: String? = null,
    val encryptionKey: String? = null,
    val port: Int? = null
)

/**
 * Response model for settings — sensitive fields are masked,
 * readOnly fields are flagged so the UI can disable editing.
 */
@Serializable
data class AppSettingsResponse(
    val jiraHost: String? = null,
    val aiProviderUrl: String? = null,
    val jwtSecret: String? = null,
    val encryptionKey: String? = null,
    val port: Int? = null,
    val portReadOnly: Boolean = true
) {
    companion object {
        /**
         * Build a masked response from raw settings.
         * jwtSecret and encryptionKey show only the last 4 characters.
         * port is marked readOnly.
         */
        fun fromSettings(settings: AppSettings): AppSettingsResponse =
            AppSettingsResponse(
                jiraHost = settings.jiraHost,
                aiProviderUrl = settings.aiProviderUrl,
                jwtSecret = settings.jwtSecret?.maskToLast4(),
                encryptionKey = settings.encryptionKey?.maskToLast4(),
                port = settings.port,
                portReadOnly = true
            )

        private fun String.maskToLast4(): String =
            if (length <= 4) this
            else "${"*".repeat(length - 4)}${takeLast(4)}"
    }
}
