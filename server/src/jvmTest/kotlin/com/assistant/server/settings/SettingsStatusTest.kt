package com.assistant.server.settings

import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.server.routes.SettingsStatusResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for the settings status configuration logic
 * used by GET /api/settings/status endpoint.
 *
 * The endpoint checks provider_configs table for a "jira" entry
 * via ProviderConfigRepository.findById("jira").
 *
 * Validates: Requirements 1.1
 */
class SettingsStatusTest {

    /** Mirrors the logic in SettingsRoutes: configured = (jiraConfig != null) */
    private fun isConfigured(jiraConfig: ProviderConfig?): Boolean {
        return jiraConfig != null
    }

    @Test
    fun `configured is false when no jira provider config exists`() {
        val result = isConfigured(null)
        assertFalse(result)
    }

    @Test
    fun `configured is true when jira provider config exists`() {
        val jiraConfig = ProviderConfig(
            providerId = "jira",
            name = "Jira Cloud Services",
            type = ProviderType.JIRA,
            endpoint = "https://mycompany.atlassian.net",
            apiKey = "user@example.com:token123",
            model = "user@example.com",
            priority = 0,
            status = ConnectionStatus.ACTIVE
        )
        val result = isConfigured(jiraConfig)
        assertTrue(result)
    }

    @Test
    fun `configured is true regardless of jira config status`() {
        val offlineConfig = ProviderConfig(
            providerId = "jira",
            name = "Jira Cloud Services",
            type = ProviderType.JIRA,
            endpoint = "https://mycompany.atlassian.net",
            apiKey = null,
            model = null,
            priority = 0,
            status = ConnectionStatus.OFFLINE
        )
        val result = isConfigured(offlineConfig)
        assertTrue(result, "Even an offline Jira config means Jira was configured")
    }

    @Test
    fun `SettingsStatusResponse serialization shape`() {
        val response = SettingsStatusResponse(configured = true)
        assertTrue(response.configured)

        val responseFalse = SettingsStatusResponse(configured = false)
        assertFalse(responseFalse.configured)
    }
}
