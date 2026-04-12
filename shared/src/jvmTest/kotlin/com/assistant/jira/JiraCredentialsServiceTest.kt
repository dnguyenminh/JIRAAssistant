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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JiraCredentialsServiceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: JiraDatabase
    private lateinit var providerConfigRepo: ProviderConfigRepository
    private lateinit var service: JiraCredentialsService

    private val testEncryptionKey = "test-encryption-key-for-aes256!"

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        database = JiraDatabase(driver)
        providerConfigRepo = ProviderConfigRepository(database, testEncryptionKey)
        service = JiraCredentialsService(providerConfigRepo)
    }

    @Test
    fun `returns null when no jira config exists`() {
        val result = service.getJiraCredentials()
        assertNull(result)
    }

    @Test
    fun `returns credentials when jira config is saved`() {
        val config = ProviderConfig(
            providerId = "jira",
            name = "Jira Cloud Services",
            type = ProviderType.JIRA,
            endpoint = "https://myorg.atlassian.net",
            apiKey = "user@example.com:my-secret-token",
            model = "user@example.com",
            priority = 0,
            status = ConnectionStatus.ACTIVE
        )
        providerConfigRepo.save(config)

        val result = service.getJiraCredentials()

        assertNotNull(result)
        assertEquals("https://myorg.atlassian.net", result.domain)
        assertEquals("user@example.com", result.email)
        assertEquals("my-secret-token", result.apiToken)
    }

    @Test
    fun `returns null when apiKey is blank`() {
        val config = ProviderConfig(
            providerId = "jira",
            name = "Jira Cloud Services",
            type = ProviderType.JIRA,
            endpoint = "https://myorg.atlassian.net",
            apiKey = "",
            model = "",
            priority = 0,
            status = ConnectionStatus.ACTIVE
        )
        providerConfigRepo.save(config)

        assertNull(service.getJiraCredentials())
    }

    @Test
    fun `returns null when apiKey has no colon separator`() {
        val config = ProviderConfig(
            providerId = "jira",
            name = "Jira Cloud Services",
            type = ProviderType.JIRA,
            endpoint = "https://myorg.atlassian.net",
            apiKey = "no-colon-here",
            model = "",
            priority = 0,
            status = ConnectionStatus.ACTIVE
        )
        providerConfigRepo.save(config)

        assertNull(service.getJiraCredentials())
    }

    @Test
    fun `handles apiToken containing colons`() {
        // API tokens can contain colons — only split on the first one
        val config = ProviderConfig(
            providerId = "jira",
            name = "Jira Cloud Services",
            type = ProviderType.JIRA,
            endpoint = "https://myorg.atlassian.net",
            apiKey = "user@example.com:token:with:colons",
            model = "user@example.com",
            priority = 0,
            status = ConnectionStatus.ACTIVE
        )
        providerConfigRepo.save(config)

        val result = service.getJiraCredentials()

        assertNotNull(result)
        assertEquals("user@example.com", result.email)
        assertEquals("token:with:colons", result.apiToken)
    }
}
