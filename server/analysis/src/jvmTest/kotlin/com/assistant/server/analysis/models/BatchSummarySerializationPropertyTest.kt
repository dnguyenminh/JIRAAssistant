package com.assistant.server.analysis.models

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/** Arbitrary generator for [BatchSummary] with realistic field ranges. */
fun Arb.Companion.arbBatchSummary(): Arb<BatchSummary> = arbitrary {
    BatchSummary(
        batchIndex = Arb.int(0..50).bind(),
        ticketIds = Arb.list(Arb.string(1..20), 0..10).bind(),
        requirementsSummary = Arb.string(0..200).bind(),
        technicalInsights = Arb.string(0..200).bind(),
        dependencySummary = Arb.string(0..200).bind(),
        keyFindings = Arb.list(Arb.string(1..50), 0..5).bind(),
        openQuestions = Arb.list(Arb.string(1..50), 0..5).bind()
    )
}

/** Strips markdown JSON fences from a string, mimicking AI response cleanup. */
private fun stripMarkdownFences(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
        return trimmed.removePrefix("```json").removeSuffix("```").trim()
    }
    if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
        return trimmed.removePrefix("```").removeSuffix("```").trim()
    }
    return trimmed
}

/**
 * Property 7: BatchSummary Serialization Round-trip.
 * Property 8: BatchSummary Parsing Tolerance.
 *
 * **Validates: Requirements 3.5, 8.1, 8.2, 8.3, 8.4**
 */
@OptIn(ExperimentalKotest::class)
class BatchSummarySerializationPropertyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cfg = PropTestConfig(iterations = 100)

    // ── Property 7: Round-trip ──────────────────────────────

    /**
     * **Validates: Requirements 3.5, 8.4**
     *
     * For any valid BatchSummary, encoding to JSON then decoding
     * produces an equivalent object.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 7: Serialization Round-trip")
    fun `serialization round-trip preserves all fields`() {
        runBlocking {
            checkAll(cfg, Arb.arbBatchSummary()) { original ->
                val encoded = json.encodeToString(original)
                val restored = json.decodeFromString<BatchSummary>(encoded)
                assertEquals(original, restored) {
                    "Round-trip mismatch: original=$original"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.5, 8.4**
     *
     * Double round-trip produces identical JSON output.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 7: Serialization Round-trip")
    fun `double round-trip produces identical JSON`() {
        runBlocking {
            checkAll(cfg, Arb.arbBatchSummary()) { original ->
                val json1 = json.encodeToString(original)
                val restored = json.decodeFromString<BatchSummary>(json1)
                val json2 = json.encodeToString(restored)
                assertEquals(json1, json2) {
                    "JSON differs after double round-trip"
                }
            }
        }
    }

    // ── Property 8: Parsing Tolerance ───────────────────────

    /**
     * **Validates: Requirements 8.1**
     *
     * Wrapping valid JSON in markdown fences still parses after stripping.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 8: Parsing Tolerance")
    fun `markdown fence stripping allows parsing`() {
        runBlocking {
            checkAll(cfg, Arb.arbBatchSummary()) { original ->
                val rawJson = json.encodeToString(original)
                val fenced = "```json\n$rawJson\n```"
                val stripped = stripMarkdownFences(fenced)
                val restored = json.decodeFromString<BatchSummary>(stripped)
                assertEquals(original, restored) {
                    "Fence-stripped parse mismatch"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 8.2**
     *
     * JSON with extra unknown fields parses with ignoreUnknownKeys.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 8: Parsing Tolerance")
    fun `unknown fields are tolerated`() {
        runBlocking {
            checkAll(cfg, Arb.arbBatchSummary()) { original ->
                val rawJson = json.encodeToString(original)
                val withExtra = rawJson.replaceFirst(
                    "{",
                    """{"_extraField":"ignored","_num":42,"""
                )
                val restored = json.decodeFromString<BatchSummary>(withExtra)
                assertEquals(original, restored) {
                    "Unknown-field tolerance failed"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 8.3**
     *
     * JSON missing optional fields uses default values.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 8: Parsing Tolerance")
    fun `missing optional fields use defaults`() {
        val minimal = "{}"
        val result = json.decodeFromString<BatchSummary>(minimal)
        assertEquals(0, result.batchIndex)
        assertTrue(result.ticketIds.isEmpty())
        assertEquals("", result.requirementsSummary)
        assertEquals("", result.technicalInsights)
        assertEquals("", result.dependencySummary)
        assertTrue(result.keyFindings.isEmpty())
        assertTrue(result.openQuestions.isEmpty())
    }
}
