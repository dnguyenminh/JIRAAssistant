package com.assistant.server.db

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 1: Configuration parsing with defaults
 *
 * For any map of environment variables (with some keys present and some
 * absent), constructing a DatabaseConfig SHALL produce a config where
 * present keys use their provided values and absent keys use their
 * documented defaults (DATABASE_USER → "postgres",
 * DATABASE_POOL_SIZE → 10).
 *
 * **Validates: Requirements 2.3, 8.1, 8.2, 8.3**
 *
 * Feature: postgresql-pgvector-migration, Property 1: Configuration parsing with defaults
 */
@OptIn(ExperimentalKotest::class)
class DatabaseConfigPropertyTest {

    private val arbJdbcUrl: Arb<String> =
        Arb.string(5..80, Codepoint.alphanumeric())
            .map { "jdbc:postgresql://host:5432/$it" }

    private val arbUsername: Arb<String> =
        Arb.string(1..30, Codepoint.alphanumeric())

    private val arbPassword: Arb<String> =
        Arb.string(0..50, Codepoint.alphanumeric())

    private val arbPoolSize: Arb<Int> = Arb.int(1..100)

    /** Whether each key is present in the env map. */
    private val arbPresence: Arb<Boolean> = Arb.boolean()

    @Test
    fun `Property 1 - present keys use provided values`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbJdbcUrl,
                arbUsername,
                arbPassword,
                arbPoolSize
            ) { url, user, pass, pool ->
                val env = mapOf(
                    "DATABASE_URL" to url,
                    "DATABASE_USER" to user,
                    "DATABASE_PASSWORD" to pass,
                    "DATABASE_POOL_SIZE" to pool.toString()
                )
                val cfg = DatabaseConfig.fromEnvMap(env)

                assertEquals(url, cfg.jdbcUrl)
                assertEquals(user, cfg.username)
                assertEquals(pass, cfg.password)
                assertEquals(pool, cfg.maxPoolSize)
            }
        }
    }

    @Test
    fun `Property 1 - absent keys use documented defaults`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbPresence, // DATABASE_URL present?
                arbPresence, // DATABASE_USER present?
                arbPresence, // DATABASE_PASSWORD present?
                arbPresence  // DATABASE_POOL_SIZE present?
            ) { urlP, userP, passP, poolP ->
                val env = buildMap {
                    if (urlP) put("DATABASE_URL", "jdbc:postgresql://h:5432/db")
                    if (userP) put("DATABASE_USER", "custom_user")
                    if (passP) put("DATABASE_PASSWORD", "secret")
                    if (poolP) put("DATABASE_POOL_SIZE", "20")
                }
                val cfg = DatabaseConfig.fromEnvMap(env)

                assertDefaults(cfg, env)
            }
        }
    }

    @Test
    fun `Property 1 - empty env map yields all defaults`() {
        val cfg = DatabaseConfig.fromEnvMap(emptyMap())

        assertEquals("", cfg.jdbcUrl)
        assertEquals("postgres", cfg.username)
        assertEquals("", cfg.password)
        assertEquals(10, cfg.maxPoolSize)
    }

    @Test
    fun `Property 1 - invalid pool size falls back to default`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(1..10, Codepoint.az())
            ) { nonNumeric ->
                val env = mapOf("DATABASE_POOL_SIZE" to nonNumeric)
                val cfg = DatabaseConfig.fromEnvMap(env)

                assertEquals(
                    10, cfg.maxPoolSize,
                    "Non-numeric '$nonNumeric' should fall back to 10"
                )
            }
        }
    }

    private fun assertDefaults(cfg: DatabaseConfig, env: Map<String, String>) {
        if ("DATABASE_URL" in env) {
            assertEquals(env["DATABASE_URL"], cfg.jdbcUrl)
        } else {
            assertEquals("", cfg.jdbcUrl)
        }
        assertField(cfg.username, env["DATABASE_USER"], "postgres")
        assertField(cfg.password, env["DATABASE_PASSWORD"], "")
        assertPoolSize(cfg.maxPoolSize, env["DATABASE_POOL_SIZE"])
    }

    private fun assertField(actual: String, envVal: String?, default: String) {
        if (envVal != null) assertEquals(envVal, actual)
        else assertEquals(default, actual)
    }

    private fun assertPoolSize(actual: Int, envVal: String?) {
        val expected = envVal?.toIntOrNull() ?: 10
        assertEquals(expected, actual)
    }
}
