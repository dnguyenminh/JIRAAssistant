package com.assistant.jira

import com.assistant.kb.ProviderConfigRepository

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
     * Returns the stored Jira credentials, or null if Jira is not configured.
     *
     * The decrypted apiKey field has the format "email:apiToken".
     * If the format is unexpected (no colon separator), returns null.
     */
    fun getJiraCredentials(): JiraCredentials? {
        val config = providerConfigRepo.findById("jira") ?: return null

        val decryptedApiKey = config.apiKey
        if (decryptedApiKey.isNullOrBlank()) return null

        val separatorIndex = decryptedApiKey.indexOf(':')
        if (separatorIndex <= 0) return null

        val email = decryptedApiKey.substring(0, separatorIndex)
        val apiToken = decryptedApiKey.substring(separatorIndex + 1)

        if (email.isBlank() || apiToken.isBlank()) return null

        return JiraCredentials(
            domain = config.endpoint,
            email = email,
            apiToken = apiToken
        )
    }
}
