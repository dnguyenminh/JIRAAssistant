package com.assistant.jira

import com.assistant.kb.ProviderConfigRepository

/**
 * Represents the state of Jira credential configuration and validity.
 *
 * - [NOT_CONFIGURED]: No Jira provider config row exists in the database.
 * - [CREDENTIALS_INVALID]: A config row exists but the stored apiKey could not be decrypted
 *   (e.g., ENCRYPTION_KEY mismatch) or has an invalid format.
 * - [OK]: Credentials exist and were decrypted successfully.
 */
enum class JiraCredentialState {
    NOT_CONFIGURED,
    CREDENTIALS_INVALID,
    OK
}

/**
 * Service that reads Jira credentials from the provider_configs table.
 *
 * Credentials are stored by IntegrationRoutes (Task 47.1) as:
 *   - endpoint = domain (e.g. "https://myorg.atlassian.net")
 *   - apiKey   = "email:apiToken" (encrypted at rest by ProviderConfigRepository)
 *   - model    = email (plain, for display)
 *
 * This service decrypts the apiKey via ProviderConfigRepository.findById()
 * and splits it to extract the email and apiToken parts.
 */
class JiraCredentialsService(
    private val providerConfigRepo: ProviderConfigRepository
) {

    /**
     * Checks the current state of Jira credentials without returning the actual credentials.
     *
     * 1. If no provider config row exists for "jira" → [JiraCredentialState.NOT_CONFIGURED]
     * 2. If a row exists but the decrypted apiKey is null/blank (decrypt failure)
     *    or has an invalid format (no colon separator, blank parts) → [JiraCredentialState.CREDENTIALS_INVALID]
     * 3. If credentials decrypt and parse successfully → [JiraCredentialState.OK]
     */
    fun getCredentialState(): JiraCredentialState {
        val config = providerConfigRepo.findById("jira")
            ?: return JiraCredentialState.NOT_CONFIGURED

        val decryptedApiKey = config.apiKey
        if (decryptedApiKey.isNullOrBlank()) {
            return JiraCredentialState.CREDENTIALS_INVALID
        }

        val separatorIndex = decryptedApiKey.indexOf(':')
        if (separatorIndex <= 0) {
            return JiraCredentialState.CREDENTIALS_INVALID
        }

        val email = decryptedApiKey.substring(0, separatorIndex)
        val apiToken = decryptedApiKey.substring(separatorIndex + 1)

        if (email.isBlank() || apiToken.isBlank()) {
            return JiraCredentialState.CREDENTIALS_INVALID
        }

        return JiraCredentialState.OK
    }

    /**
     * Returns the stored Jira credentials, or null if Jira is not configured.
     *
     * The decrypted apiKey field has the format "email:apiToken".
     * If the format is unexpected (no colon separator), returns null.
     */
    fun getJiraCredentials(): JiraCredentials? {
        val config = providerConfigRepo.findById("jira")
        if (config == null) {
            println("[JiraCredentialsService] No 'jira' provider config found in DB")
            return null
        }

        val decryptedApiKey = config.apiKey
        if (decryptedApiKey.isNullOrBlank()) {
            println("[JiraCredentialsService] apiKey is null/blank for jira config")
            return null
        }

        val separatorIndex = decryptedApiKey.indexOf(':')
        if (separatorIndex <= 0) {
            println("[JiraCredentialsService] apiKey has no ':' separator")
            return null
        }

        val email = decryptedApiKey.substring(0, separatorIndex)
        val apiToken = decryptedApiKey.substring(separatorIndex + 1)

        if (email.isBlank() || apiToken.isBlank()) {
            println("[JiraCredentialsService] email or apiToken is blank")
            return null
        }

        return JiraCredentials(
            domain = config.endpoint,
            email = email,
            apiToken = apiToken
        )
    }
}
