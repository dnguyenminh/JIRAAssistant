package com.assistant.server.testing

import com.assistant.server.config.ServerConfig

/**
 * Factory for creating [ServerConfig] instances pre-configured
 * for unit and integration tests.
 *
 * Place shared test utilities, fixtures, and test doubles in this
 * module so that other server sub-modules can depend on them via
 * `implementation(project(":server:testing-support"))`.
 */
object TestConfigFactory {

    /** Minimal [ServerConfig] suitable for unit tests that don't need a real DB. */
    fun minimal(): ServerConfig = ServerConfig(
        jiraHost = "https://jira.example.com",
        aiProviderUrl = "http://localhost:11434",
        jwtSecret = "test-jwt-secret-change-in-production",
        encryptionKey = "test-encryption-key-change-in-prod",
        port = 8080,
        staticDir = "./static",
    )
}
