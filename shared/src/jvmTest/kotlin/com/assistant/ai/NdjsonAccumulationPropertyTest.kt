package com.assistant.ai

import com.assistant.ai.models.OllamaStreamLine
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.kotest.common.ExperimentalKotest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 1: NDJSON accumulation preserves all response text.
 *
 * For any sequence of OllamaStreamLine objects with random response
 * strings, the accumulated result returned by OllamaStreamReader
 * equals the exact concatenation of all response fields in order.
 *
 * **Validates: Requirements 1.2, 1.3**
 *
 * Feature: streaming-generation-progress,
 * Property 1: NDJSON accumulation preserves all response text
 */
class NdjsonAccumulationPropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Generates a random OllamaStreamLine with done=false. */
    private fun arbStreamLine(): Arb<OllamaStreamLine> = arbitrary {
        OllamaStreamLine(
            model = "test-model",
            response = Arb.string(0..50).bind(),
            done = false,
            createdAt = "2025-01-01T00:00:00Z"
        )
    }

    /**
     * Builds NDJSON content from a list of stream lines,
     * appending a final line with done=true.
     */
    private fun buildNdjson(lines: List<OllamaStreamLine>): String {
        val sb = StringBuilder()
        for (line in lines) {
            sb.appendLine(json.encodeToString(line))
        }
        val finalLine = OllamaStreamLine(
            model = "test-model",
            response = "",
            done = true,
            doneReason = "stop",
            createdAt = "2025-01-01T00:00:00Z"
        )
        sb.appendLine(json.encodeToString(finalLine))
        return sb.toString()
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 1 - accumulated text equals concatenation of all response fields`() = runTest {
        val reader = OllamaStreamReader()

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(arbStreamLine(), 1..200)
        ) { streamLines ->
            val expectedText = streamLines.joinToString("") { it.response }
            val ndjsonContent = buildNdjson(streamLines)
            val channel = ByteReadChannel(ndjsonContent.encodeToByteArray())

            val progressValues = mutableListOf<Int>()
            val result = reader.readStream(channel) { progressValues.add(it) }

            assertEquals(
                expectedText,
                result,
                "Accumulated text must equal concatenation of all response fields " +
                    "(${streamLines.size} lines)"
            )
        }
    }
}
