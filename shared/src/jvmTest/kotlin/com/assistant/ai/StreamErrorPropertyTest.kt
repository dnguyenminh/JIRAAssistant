package com.assistant.ai

import com.assistant.ai.models.OllamaStreamLine
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.ktor.utils.io.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Property 2: Stream error never produces partial success.
 *
 * For any NDJSON stream interrupted at any point before
 * `done: true` (network error, timeout), the result SHALL
 * always be `AIResult.Failure` — never `AIResult.Success`
 * with partial text.
 *
 * **Validates: Requirements 1.4**
 *
 * Feature: streaming-generation-progress,
 * Property 2: Stream error never produces partial success
 */
class StreamErrorPropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun arbStreamLine(): Arb<OllamaStreamLine> = arbitrary {
        OllamaStreamLine(
            model = "test-model",
            response = Arb.string(1..40).bind(),
            done = false,
            createdAt = "2025-01-01T00:00:00Z"
        )
    }

    /**
     * Mirrors OllamaAgent.analyzeStreaming contract: reads
     * stream via OllamaStreamReader, catches exceptions and
     * returns AIResult.Failure. Never returns partial Success.
     */
    private suspend fun simulateAnalyzeStreaming(
        channel: ByteReadChannel,
        onProgress: (Int) -> Unit
    ): AIResult {
        return try {
            val reader = OllamaStreamReader()
            val text = reader.readStream(channel, onProgress)
            AIResult.Success(text)
        } catch (e: Exception) {
            AIResult.Failure("Stream error: ${e.message}")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 2 - stream error never produces partial success`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.list(arbStreamLine(), 2..50),
                Arb.int(1..49)
            ) { streamLines, rawFailIndex ->
                val failAt = rawFailIndex % streamLines.size + 1
                val channel = ByteChannel(autoFlush = true)

                // Write partial lines then close with error
                launch {
                    for (i in 0 until failAt) {
                        val line = json.encodeToString(
                            streamLines[i]
                        ) + "\n"
                        channel.writeFully(
                            line.encodeToByteArray()
                        )
                    }
                    channel.close(IOException("Connection reset"))
                }

                val result = simulateAnalyzeStreaming(channel) {}

                assertIs<AIResult.Failure>(
                    result,
                    "Stream error at line $failAt/" +
                        "${streamLines.size} must yield Failure"
                )
            }
        }
}
