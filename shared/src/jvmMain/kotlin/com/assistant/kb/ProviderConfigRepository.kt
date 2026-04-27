package com.assistant.kb

import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.db.JiraDatabase
import com.assistant.security.CryptoUtils

/**
 * Repository for provider configurations with encryption at rest.
 * The api_key field is encrypted using AES-256-GCM before writing to SQLite
 * and decrypted when reading.
 *
 * Open for extension by PostgreSQL-backed implementation.
 */
open class ProviderConfigRepository(
    private val database: JiraDatabase? = null,
    private val encryptionKey: String = ""
) {

    /**
     * Get all provider configs, decrypting api_key fields.
     */
    open fun getAllProviders(): List<ProviderConfig> {
        val db = database ?: return emptyList()
        return db.knowledgeBaseQueries.getAllProviders().executeAsList().map { row ->
            ProviderConfig(
                providerId = row.provider_id,
                name = row.name,
                type = ProviderType.valueOf(row.type),
                endpoint = row.endpoint,
                apiKey = row.api_key?.let { decryptApiKey(it) },
                model = row.model,
                priority = row.priority.toInt(),
                status = ConnectionStatus.valueOf(row.status)
            )
        }
    }

    /**
     * Get a single provider config by ID, decrypting api_key.
     */
    open fun findById(providerId: String): ProviderConfig? {
        val db = database ?: return null
        val row = db.knowledgeBaseQueries.findProviderById(providerId).executeAsOneOrNull()
            ?: return null
        return ProviderConfig(
            providerId = row.provider_id,
            name = row.name,
            type = ProviderType.valueOf(row.type),
            endpoint = row.endpoint,
            apiKey = row.api_key?.let { decryptApiKey(it) },
            model = row.model,
            priority = row.priority.toInt(),
            status = ConnectionStatus.valueOf(row.status)
        )
    }

    /**
     * Save a provider config, encrypting the api_key before writing.
     */
    open fun save(config: ProviderConfig): Boolean {
        val db = database ?: return false
        return try {
            db.knowledgeBaseQueries.insertOrReplaceProvider(
                provider_id = config.providerId,
                name = config.name,
                type = config.type.name,
                endpoint = config.endpoint,
                api_key = config.apiKey?.let { encryptApiKey(it) },
                model = config.model,
                priority = config.priority.toLong(),
                status = config.status.name
            )
            true
        } catch (e: Exception) {
            System.err.println("Failed to save provider config: ${e.message}")
            false
        }
    }

    /**
     * Find first provider config by type (e.g., OLLAMA, JIRA).
     * Avoids hardcoding provider IDs which may differ from defaults.
     */
    open fun findByType(type: ProviderType): ProviderConfig? {
        return getAllProviders().firstOrNull { it.type == type }
    }

    /**
     * Check if any provider of given type exists.
     */
    open fun existsByType(type: ProviderType): Boolean {
        return getAllProviders().any { it.type == type }
    }

    /**
     * Update only the status of a provider.
     */
    open fun updateStatus(providerId: String, status: ConnectionStatus) {
        val db = database ?: return
        db.knowledgeBaseQueries.updateProviderStatus(
            status = status.name,
            provider_id = providerId
        )
    }

    private fun encryptApiKey(plaintext: String): String {
        return CryptoUtils.encryptAES256GCM(plaintext, encryptionKey)
    }

    private fun decryptApiKey(ciphertext: String): String {
        return try {
            CryptoUtils.decryptAES256GCM(ciphertext, encryptionKey)
        } catch (e: Exception) {
            System.err.println("Failed to decrypt api_key: ${e.message}")
            "" // Return empty string on decryption failure
        }
    }
}
