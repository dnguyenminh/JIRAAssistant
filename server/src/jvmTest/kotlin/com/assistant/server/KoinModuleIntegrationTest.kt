package com.assistant.server

import com.assistant.server.config.ServerConfig
import com.assistant.server.di.serverModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Koin DI integration test — verifies the composed
 * [serverModule] can be loaded without duplicate or
 * missing definition errors.
 *
 * This catches structural issues (duplicate bindings,
 * missing includes) introduced during the module
 * restructuring. Full resolution of every binding is
 * not tested here because many singletons require a
 * live database or network; that is covered by the
 * sequential integration tests instead.
 */
class KoinModuleIntegrationTest {

    private val testConfig = ServerConfig(
        jwtSecret = "test-jwt-secret-for-koin-check",
        encryptionKey = "test-encryption-key-0123456",
        port = 0,
        staticDir = "./static",
    )

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `serverModule composes without duplicate or missing definitions`() {
        assertDoesNotThrow {
            startKoin {
                modules(serverModule(testConfig))
            }
        }
    }
}
