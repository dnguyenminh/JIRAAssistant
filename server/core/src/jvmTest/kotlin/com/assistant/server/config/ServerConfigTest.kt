package com.assistant.server.config

import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for ServerConfig.loadFromDb — verifies DB-first fallback to env vars.
 */
class ServerConfigTest {

    /** Simple in-memory SettingsRepository for testing. */
    private class FakeSettingsRepository(
        private val store: MutableMap<String, String> = mutableMapOf()
    ) : SettingsRepository {
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }

    @Test
    fun `loadFromDb uses DB values when present`() = runBlocking {
        val repo = FakeSettingsRepository(mutableMapOf(
            "JWT_SECRET" to "db-secret",
            "ENCRYPTION_KEY" to "db-enc-key",
            "PORT" to "9090"
        ))

        val config = ServerConfig.loadFromDb(repo)

        assertEquals("db-secret", config.jwtSecret)
        assertEquals("db-enc-key", config.encryptionKey)
        assertEquals(9090, config.port)
    }

    @Test
    fun `loadFromDb falls back to defaults when DB is empty`() = runBlocking {
        val repo = FakeSettingsRepository()

        val config = ServerConfig.loadFromDb(repo)

        assertNotNull(config.jwtSecret)
        assertNotNull(config.encryptionKey)
        assertNotNull(config.staticDir)
        assertTrue(config.port > 0)
    }

    @Test
    fun `loadFromDb always reads staticDir from env, not DB`() = runBlocking {
        val repo = FakeSettingsRepository(mutableMapOf(
            "STATIC_DIR" to "/should/be/ignored"
        ))

        val config = ServerConfig.loadFromDb(repo)

        assertNotEquals("/should/be/ignored", config.staticDir)
    }

    @Test
    fun `loadFromDb handles invalid PORT in DB gracefully`() = runBlocking {
        val repo = FakeSettingsRepository(mutableMapOf(
            "PORT" to "not-a-number"
        ))

        val config = ServerConfig.loadFromDb(repo)

        assertTrue(config.port > 0)
    }

    @Test
    fun `load returns config from env vars only`() {
        val config = ServerConfig.load()

        assertNotNull(config.jwtSecret)
        assertNotNull(config.encryptionKey)
        assertNotNull(config.staticDir)
        assertTrue(config.port > 0)
    }
}
