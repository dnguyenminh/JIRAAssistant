package com.assistant.jira

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.db.JiraDatabase
import com.assistant.kb.ProviderConfigRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [JiraCredentialsService.getCredentialState] covering all three states:
 * - NOT_CONFIGURED: no jira provider config row exists
 * - CREDENTIALS_INVALID: config exists but apiKey is null/blank or has invalid format
 * - OK: credentials decrypt and parse successfully
 *
 * Validates: Requirements 2.1, 2.2
 */
class JiraCredentialStateTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var providerConfigRepo: ProviderConfigRepository
    private lateinit var service: JiraCredentialsService

    private val testEncryptionKey = "test-encryption-key-for-aes256!"

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        val database = JiraDatabase(driver)
        providerConfigRepo = ProviderConfigRepository(database, testEncryptionKey)
        service = JiraCredentialsService(providerConfigRepo)
    }

    // --- NOT_CONFIGURED ---

    @Test
    fun `returns NOT_CONFIGURED when no jira config exists`() {
        assertEquals(JiraCredentialState.NOT_CONFIGURED, service.getCredentialState())
    }

    // --- CREDENTIALS_INVALID ---

    @Test
    fun `returns CREDENTIALS_INVALID when apiKey is blank`() {
        saveJiraConfig(apiKey = "")
        assertEquals(JiraCredentialState.CREDENTIALS_INVALID, service.getCredentialState())
    }

    @Test
    fun `returns CREDENTIALS_INVALID when apiKey has no colon separator`() {
        saveJiraConfig(apiKey = "no-colon-here")
        assertEquals(JiraCredentialState.CREDENTIALS_INVALID, service.getCredentialState())
    }

    @Test
    fun `returns CREDENTIALS_INVALID when email part is blank`() {
        saveJiraConfig(apiKey = ":some-token")
        assertEquals(JiraCredentialState.CREDENTIALS_INVALID, service.getCredentialState())
    }

    @Test
    fun `returns CREDENTIALS_INVALID when apiToken part is blank`() {
        saveJiraConfig(apiKey = "user@example.com:")
        assertEquals(JiraCredentialState.CREDENTIALS_INVALID, service.getCredentialState())
    }

    @Test
    fun `returns CREDENTIALS_INVALID when decrypt fails with wrong key`() {
        // Save with correct key, then read with wrong key
        saveJiraConfig(apiKey = "user@example.com:valid-token")
        val wrongKeyRepo = ProviderConfigRepository(
            JiraDatabase(driver), "wrong-encryption-key-for-test!"
        )
        val wrongKeyService = JiraCredentialsService(wrongKeyRepo)
        assertEquals(JiraCredentialState.CREDENTIALS_INVALID, wrongKeyService.getCredentialState())
    }

    // --- OK ---

    @Test
    fun `returns OK when credentials are valid`() {
        saveJiraConfig(apiKey = "user@example.com:my-secret-token")
        assertEquals(JiraCredentialState.OK, service.getCredentialState())
    }

    @Test
    fun `returns OK when apiToken contains colons`() {
        saveJiraConfig(apiKey = "user@example.com:token:with:colons")
        assertEquals(JiraCredentialState.OK, service.getCredentialState())
    }

    // --- Helper ---

    private fun saveJiraConfig(apiKey: String) {
        providerConfigRepo.save(
            ProviderConfig(
                providerId = "jira",
                name = "Jira Cloud Services",
                type = ProviderType.JIRA,
                endpoint = "https://myorg.atlassian.net",
                apiKey = apiKey,
                model = "user@example.com",
                priority = 0,
                status = ConnectionStatus.ACTIVE
            )
        )
    }
}
