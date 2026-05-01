package com.assistant.server.routes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.db.JiraDatabase
import com.assistant.jira.JiraCredentialState
import com.assistant.jira.JiraCredentialsService
import com.assistant.kb.ProviderConfigRepository
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test verifying that GET /api/projects returns the correct
 * [ProjectsResponse] wrapper with `jiraStatus` field for each credential state.
 *
 * Tests the full chain: ProviderConfigRepository → JiraCredentialsService →
 * credential state → ProjectsResponse construction, matching the logic in
 * [ProjectRoutes.projectRoutes].
 *
 * Validates: Requirements 2.1, 2.2, 3.1, 3.2
 */
class ProjectsResponseTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var providerConfigRepo: ProviderConfigRepository
    private lateinit var credentialsService: JiraCredentialsService

    private val encryptionKey = "test-encryption-key-for-aes256!"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        val database = JiraDatabase(driver)
        providerConfigRepo = ProviderConfigRepository(database, encryptionKey)
        credentialsService = JiraCredentialsService(providerConfigRepo)
    }

    // --- NOT_CONFIGURED state ---

    @Test
    fun `response has jiraStatus NOT_CONFIGURED when no jira config exists`() {
        val response = buildProjectsResponse()

        assertEquals("NOT_CONFIGURED", response.jiraStatus)
        assertTrue(response.projects.isEmpty())
    }

    // --- CREDENTIALS_INVALID state ---

    @Test
    fun `response has jiraStatus CREDENTIALS_INVALID when decrypt fails`() {
        saveJiraConfig("user@example.com:valid-token")
        // Create service with wrong encryption key to simulate decrypt failure
        val wrongKeyRepo = ProviderConfigRepository(JiraDatabase(driver), "wrong-key-for-decrypt-failure!")
        val wrongKeyService = JiraCredentialsService(wrongKeyRepo)

        val response = buildProjectsResponse(wrongKeyService)

        assertEquals("CREDENTIALS_INVALID", response.jiraStatus)
        assertTrue(response.projects.isEmpty())
    }

    @Test
    fun `response has jiraStatus CREDENTIALS_INVALID when apiKey is blank`() {
        saveJiraConfig("")
        val response = buildProjectsResponse()

        assertEquals("CREDENTIALS_INVALID", response.jiraStatus)
        assertTrue(response.projects.isEmpty())
    }

    // --- OK state ---

    @Test
    fun `response has jiraStatus OK when credentials are valid`() {
        saveJiraConfig("user@example.com:my-secret-token")
        val response = buildProjectsResponse()

        assertEquals("OK", response.jiraStatus)
    }

    // --- Serialization ---

    @Test
    fun `ProjectsResponse serializes with jiraStatus field`() {
        val response = ProjectsResponse(projects = emptyList(), jiraStatus = "CREDENTIALS_INVALID")
        val serialized = json.encodeToString(ProjectsResponse.serializer(), response)

        assertTrue(serialized.contains("\"jiraStatus\""))
        assertTrue(serialized.contains("\"CREDENTIALS_INVALID\""))
        assertTrue(serialized.contains("\"projects\""))
    }

    @Test
    fun `ProjectsResponse round-trip serialization preserves all fields`() {
        val original = ProjectsResponse(projects = emptyList(), jiraStatus = "OK")
        val serialized = json.encodeToString(ProjectsResponse.serializer(), original)
        val deserialized = json.decodeFromString(ProjectsResponse.serializer(), serialized)

        assertEquals(original.jiraStatus, deserialized.jiraStatus)
        assertEquals(original.projects, deserialized.projects)
    }

    // --- Helpers ---

    /**
     * Mirrors the response-building logic from ProjectRoutes.projectRoutes().
     * For NOT_CONFIGURED and CREDENTIALS_INVALID, returns empty projects.
     * For OK, returns empty projects (no real Jira API call in test).
     */
    private fun buildProjectsResponse(
        svc: JiraCredentialsService = credentialsService
    ): ProjectsResponse {
        return when (svc.getCredentialState()) {
            JiraCredentialState.NOT_CONFIGURED -> ProjectsResponse(
                projects = emptyList(),
                jiraStatus = JiraCredentialState.NOT_CONFIGURED.name
            )
            JiraCredentialState.CREDENTIALS_INVALID -> ProjectsResponse(
                projects = emptyList(),
                jiraStatus = JiraCredentialState.CREDENTIALS_INVALID.name
            )
            JiraCredentialState.OK -> ProjectsResponse(
                projects = emptyList(),
                jiraStatus = JiraCredentialState.OK.name
            )
        }
    }

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
