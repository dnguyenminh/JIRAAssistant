package com.assistant.ai

import com.assistant.ai.models.OllamaStreamLine
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json

/** Thrown when NDJSON stream ends without a `done: true` signal. */
class StreamInterruptedException(message: String) : Exception(message)

private const val INITIAL_ESTIMATED_LINES = 1000
private const val MAX_PROGRESS_BEFORE_DONE = 95

/**
 * Reads NDJSON lines from a ByteReadChannel, accumulates response text,
 * and reports progress via callback. Extracted from OllamaAgent to keep
 * file sizes within limits (SRP).
 *
 * Requirements: 1.2, 1.3, 1.5, 3.1, 3.2, 3.3
 */
internal class OllamaStreamReader(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    /**
     * Reads streaming NDJSON lines, accumulates text, reports progress.
     * Returns the full accumulated response text.
     * Throws on stream interruption (caller wraps in AIResult.Failure).
     */
    suspend fun readStream(
        channel: ByteReadChannel,
        onProgress: (Int) -> Unit
    ): String {
        val accumulated = StringBuilder()
        var linesReceived = 0
        var estimatedTotalLines = INITIAL_ESTIMATED_LINES

        var doneReceived = false
        var maxProgress = 0

        while (!channel.isClosedForRead) {
            val line = channel.readLine() ?: break
            if (line.isBlank()) continue

            val streamLine = parseLine(line) ?: continue
            accumulated.append(streamLine.response)
            linesReceived++

            if (streamLine.done) {
                doneReceived = true
                onProgress(100)
                return accumulated.toString()
            }

            estimatedTotalLines = adjustEstimate(linesReceived, estimatedTotalLines)
            val raw = calculateProgress(linesReceived, estimatedTotalLines)
            maxProgress = maxOf(maxProgress, raw)
            onProgress(maxProgress)
        }

        if (!doneReceived) {
            throw StreamInterruptedException(
                "Stream ended without done signal after $linesReceived lines"
            )
        }
        return accumulated.toString()
    }

    private fun parseLine(line: String): OllamaStreamLine? {
        return try {
            json.decodeFromString<OllamaStreamLine>(line)
        } catch (_: Exception) {
            null // Skip malformed lines per error handling spec
        }
    }
}

/**
 * Calculates streaming progress with cap at 95% before done.
 * Visible for testing.
 *
 * Requirements: 3.2
 */
internal fun calculateProgress(linesReceived: Int, estimatedTotalLines: Int): Int {
    if (estimatedTotalLines <= 0) return 0
    return minOf(MAX_PROGRESS_BEFORE_DONE, (linesReceived * 100) / estimatedTotalLines)
}

/**
 * Adjusts estimated total lines by doubling when exceeded.
 * Visible for testing.
 *
 * Requirements: 3.3
 */
internal fun adjustEstimate(linesReceived: Int, currentEstimate: Int): Int {
    return if (linesReceived > currentEstimate) currentEstimate * 2 else currentEstimate
}
