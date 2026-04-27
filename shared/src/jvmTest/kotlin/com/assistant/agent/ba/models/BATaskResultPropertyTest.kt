package com.assistant.agent.ba.models

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Feature: agent-subprocess-orchestration
 * Property 10: BATaskResult JSON serialization round-trip
 *
 * For any valid BATaskResult with arbitrary document string,
 * non-negative toolCallsExecuted/toolCallsFailed/totalDurationMs,
 * random status from BATaskStatus values, and random-length list
 * of valid ToolCallLogEntry objects, serializing to JSON then
 * deserializing back produces an equivalent object.
 *
 * **Validates: Requirements 6.2, 6.3, 6.5**
 */
class BATaskResultPropertyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val arbToolCallLogEntry: Arb<ToolCallLogEntry> = arbitrary {
        ToolCallLogEntry(
            toolName = Arb.string(minSize = 1, maxSize = 30).bind(),
            durationMs = Arb.long(min = 0L, max = 60_000L).bind(),
            success = Arb.boolean().bind(),
            resultSizeChars = Arb.int(min = 0, max = 100_000).bind()
        )
    }

    private val arbBATaskResult: Arb<BATaskResult> = arbitrary {
        BATaskResult(
            document = Arb.string(minSize = 0, maxSize = 200).bind(),
            toolCallsExecuted = Arb.int(min = 0, max = 100).bind(),
            toolCallsFailed = Arb.int(min = 0, max = 50).bind(),
            totalDurationMs = Arb.long(min = 0L, max = 600_000L).bind(),
            status = Arb.element(BATaskStatus.entries.toList()).bind(),
            toolCallLog = Arb.list(arbToolCallLogEntry, range = 0..10).bind()
        )
    }

    @Test
    fun `Property 10 - BATaskResult JSON round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbBATaskResult) { result ->
            val encoded = json.encodeToString(result)
            val decoded = json.decodeFromString<BATaskResult>(encoded)
            assertEquals(result, decoded, "Round-trip failed for $result")
        }
    }
}
