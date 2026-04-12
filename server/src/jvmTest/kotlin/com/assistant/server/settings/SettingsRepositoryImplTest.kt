package com.assistant.server.settings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.db.JiraDatabase
import com.assistant.settings.SettingsRepository
import com.assistant.settings.SettingsRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: JiraDatabase
    private lateinit var repository: SettingsRepository

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        database = JiraDatabase(driver)
        repository = SettingsRepositoryImpl(database)
    }

    @Test
    fun `getAll returns empty map when no settings exist`() = runBlocking {
        val result = repository.getAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `put and get round-trip`() = runBlocking {
        repository.put("JIRA_HOST", "https://jira.example.com")

        val value = repository.get("JIRA_HOST")
        assertEquals("https://jira.example.com", value)
    }

    @Test
    fun `get returns null for missing key`() = runBlocking {
        val value = repository.get("NONEXISTENT_KEY")
        assertNull(value)
    }

    @Test
    fun `put overwrites existing value`() = runBlocking {
        repository.put("PORT", "8080")
        repository.put("PORT", "9090")

        assertEquals("9090", repository.get("PORT"))
    }

    @Test
    fun `getAll returns all stored settings`() = runBlocking {
        repository.put("KEY_A", "value_a")
        repository.put("KEY_B", "value_b")
        repository.put("KEY_C", "value_c")

        val all = repository.getAll()
        assertEquals(3, all.size)
        assertEquals("value_a", all["KEY_A"])
        assertEquals("value_b", all["KEY_B"])
        assertEquals("value_c", all["KEY_C"])
    }

    @Test
    fun `putAll stores multiple settings atomically`() = runBlocking {
        val settings = mapOf(
            "JIRA_HOST" to "https://jira.test.com",
            "AI_PROVIDER_URL" to "http://localhost:11434",
            "JWT_SECRET" to "my-secret"
        )

        repository.putAll(settings)

        val all = repository.getAll()
        assertEquals(3, all.size)
        assertEquals("https://jira.test.com", all["JIRA_HOST"])
        assertEquals("http://localhost:11434", all["AI_PROVIDER_URL"])
        assertEquals("my-secret", all["JWT_SECRET"])
    }

    @Test
    fun `putAll overwrites existing keys`() = runBlocking {
        repository.put("KEY_A", "old_value")

        repository.putAll(mapOf("KEY_A" to "new_value", "KEY_B" to "value_b"))

        assertEquals("new_value", repository.get("KEY_A"))
        assertEquals("value_b", repository.get("KEY_B"))
    }

    @Test
    fun `putAll with empty map is a no-op`() = runBlocking {
        repository.put("EXISTING", "value")

        repository.putAll(emptyMap())

        assertEquals(1, repository.getAll().size)
        assertEquals("value", repository.get("EXISTING"))
    }
}
