package com.assistant.server.agent.ba

import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Shared test configuration helpers for BA integration tests.
 *
 * Reads CLI paths and models from system properties, env vars,
 * or test.properties. Provides skip logic for unconfigured CLIs.
 */

internal val testProps: java.util.Properties? by lazy {
    val stream = BADocumentAgentIntegrationTest::class.java
        .classLoader.getResourceAsStream("test.properties")
        ?: return@lazy null
    java.util.Properties().apply { load(stream) }
}

internal fun cliPath(backend: String): String? =
    System.getProperty("test.$backend.cli.path")
        ?: System.getenv("TEST_${backend.uppercase()}_CLI_PATH")
        ?: testProps?.getProperty("test.$backend.cli.path")

internal fun cliModel(backend: String): String? =
    System.getProperty("test.$backend.cli.model")
        ?: System.getenv("TEST_${backend.uppercase()}_CLI_MODEL")
        ?: testProps?.getProperty("test.$backend.cli.model")

internal fun skipNoCli(backend: String) {
    assumeTrue(
        false,
        "Skipped: set -Dtest.$backend.cli.path, " +
            "TEST_${backend.uppercase()}_CLI_PATH env var, " +
            "or test.$backend.cli.path in test.properties"
    )
}

internal fun isMcpEnabled(): Boolean =
    System.getProperty("test.mcp.enabled")?.toBoolean()
        ?: System.getenv("TEST_MCP_ENABLED")?.toBoolean()
        ?: testProps?.getProperty("test.mcp.enabled")?.toBoolean()
        ?: false

internal fun assumeMcpAvailable() {
    assumeTrue(
        isMcpEnabled(),
        "Skipped: set test.mcp.enabled=true to run MCP tests"
    )
}

internal fun testTicketId(): String =
    testProps?.getProperty("test.brd.ticket") ?: "PROJ-123"

internal fun providerConfig(
    type: ProviderType, endpoint: String, model: String?
) = ProviderConfig(
    providerId = type.name.lowercase(),
    name = "${type.name} CLI",
    type = type, endpoint = endpoint,
    model = model, priority = 1,
    status = ConnectionStatus.ACTIVE
)
