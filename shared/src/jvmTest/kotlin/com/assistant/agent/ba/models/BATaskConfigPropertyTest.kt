package com.assistant.agent.ba.models

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Feature: agent-subprocess-orchestration
 * Property 9: BATaskConfig JSON serialization round-trip
 *
 * For any valid BATaskConfig with arbitrary non-blank rootTicketId,
 * docType in {"BRD","FSD","SLIDES"}, positive maxToolCalls,
 * positive taskTimeoutSeconds, and cliBackend in {"gemini","copilot","kiro","ollama"},
 * serializing to JSON then deserializing back produces an equivalent object.
 *
 * **Validates: Requirements 6.1, 6.4**
 */
class BATaskConfigPropertyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val arbBATaskConfig: Arb<BATaskConfig> = arbitrary {
        val ticketId = Arb.string(minSize = 1, maxSize = 50).bind()
            .let { if (it.isBlank()) "TICKET-1" else it }
        val docType = Arb.element(BATaskConfig.VALID_DOC_TYPES.toList()).bind()
        val maxToolCalls = Arb.int(min = 1, max = 1000).bind()
        val timeoutSeconds = Arb.int(min = 1, max = 3600).bind()
        val backend = Arb.element(BATaskConfig.VALID_CLI_BACKENDS.toList()).bind()

        BATaskConfig(
            rootTicketId = ticketId,
            docType = docType,
            maxToolCalls = maxToolCalls,
            taskTimeoutSeconds = timeoutSeconds,
            cliBackend = backend
        )
    }

    @Test
    fun `Property 9 - BATaskConfig JSON round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbBATaskConfig) { config ->
            val encoded = json.encodeToString(config)
            val decoded = json.decodeFromString<BATaskConfig>(encoded)
            assertEquals(config, decoded, "Round-trip failed for $config")
        }
    }
}
