package com.assistant.jira

/**
 * Holds decrypted Jira connection credentials read from the provider_configs table.
 */
data class JiraCredentials(
    val domain: String,
    val email: String,
    val apiToken: String
)
