package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ExtractionConfidence
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ResponseParserHelpers and round-trip serialization.
 * Validates: Requirements 25.5, 25.6
 */
class ResponseParserHelpersTest {

    // ── Markdown fence stripping ──

    @Test
    fun `stripMarkdownFences removes json fence`() {
        val raw = "```json\n{\"key\":\"value\"}\n```"
        assertEquals("{\"key\":\"value\"}", ResponseParserHelpers.stripMarkdownFences(raw))
    }

    @Test
    fun `stripMarkdownFences removes plain fence`() {
        val raw = "```\n{\"key\":\"value\"}\n```"
        assertEquals("{\"key\":\"value\"}", ResponseParserHelpers.stripMarkdownFences(raw))
    }

    @Test
    fun `stripMarkdownFences returns clean JSON unchanged`() {
        val raw = """{"key":"value"}"""
        assertEquals(raw, ResponseParserHelpers.stripMarkdownFences(raw))
    }

    @Test
    fun `stripMarkdownFences handles whitespace around fences`() {
        val raw = "  \n```json\n{\"a\":1}\n```\n  "
        val result = ResponseParserHelpers.stripMarkdownFences(raw)
        assertEquals("{\"a\":1}", result)
    }

    @Test
    fun `stripMarkdownFences handles fence with extra text before`() {
        val raw = "Here is the JSON:\n```json\n{\"x\":1}\n```"
        val result = ResponseParserHelpers.stripMarkdownFences(raw)
        assertEquals("{\"x\":1}", result)
    }

    // ── Req 25.6: Extraction confidence computation ──

    @Test
    fun `computeConfidence returns LOW for 0 sections`() {
        assertEquals(ExtractionConfidence.LOW, ResponseParserHelpers.computeConfidence(0))
    }

    @Test
    fun `computeConfidence returns LOW for 1 section`() {
        assertEquals(ExtractionConfidence.LOW, ResponseParserHelpers.computeConfidence(1))
    }

    @Test
    fun `computeConfidence returns MEDIUM for 2 sections`() {
        assertEquals(ExtractionConfidence.MEDIUM, ResponseParserHelpers.computeConfidence(2))
    }

    @Test
    fun `computeConfidence returns MEDIUM for 3 sections`() {
        assertEquals(ExtractionConfidence.MEDIUM, ResponseParserHelpers.computeConfidence(3))
    }

    @Test
    fun `computeConfidence returns HIGH for 4 sections`() {
        assertEquals(ExtractionConfidence.HIGH, ResponseParserHelpers.computeConfidence(4))
    }

    @Test
    fun `computeConfidence returns HIGH for 6 sections`() {
        assertEquals(ExtractionConfidence.HIGH, ResponseParserHelpers.computeConfidence(6))
    }

    // ── countSections ──

    @Test
    fun `countSections counts all true flags`() {
        val count = ResponseParserHelpers.countSections(
            hasRequirementSummary = true, hasEvolution = true,
            hasComplexity = true, hasTechnicalDetails = true,
            hasAcceptanceCriteria = true, hasDependencies = true
        )
        assertEquals(6, count)
    }

    @Test
    fun `countSections returns 0 when all false`() {
        val count = ResponseParserHelpers.countSections(
            hasRequirementSummary = false, hasEvolution = false,
            hasComplexity = false, hasTechnicalDetails = false,
            hasAcceptanceCriteria = false, hasDependencies = false
        )
        assertEquals(0, count)
    }

    // ── Req 25.5: Round-trip property ──

    @Test
    fun `round trip serialize then deserialize produces equivalent AIResponseRoot`() {
        val json = DeepAnalysisResponseParserImpl.json
        val original = AIResponseRoot(
            requirementSummary = AIRequirementSummary(
                unified = "Summary", businessSummary = "Biz",
                asIsState = "Old", toBeState = "New",
                extractedRequirements = listOf("R1"),
                affectedModules = listOf(AIAffectedModule("Mod", "PRIMARY"))
            ),
            evolution = listOf(AIEvolutionEntry("v1", "2024-01-01", "Init", "ORIGIN")),
            complexity = AIComplexity(5.0, "Medium", listOf(AIKBReference("T-1", 80.0))),
            acceptanceCriteria = listOf(AIAcceptanceCriterion("AC1", "Desc", "Auto")),
            dependencies = AIDependencies(
                blockingIssues = listOf(AIDependencyItem("B-1", "Block", "blocks", "HIGH")),
                relatedIssues = emptyList(),
                externalDependencies = listOf("Redis")
            )
        )
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<AIResponseRoot>(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `round trip with default values produces equivalent object`() {
        val json = DeepAnalysisResponseParserImpl.json
        val original = AIResponseRoot()
        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<AIResponseRoot>(serialized)
        assertEquals(original, deserialized)
    }

    // ── Confidence computed correctly in full parse ──

    @Test
    fun `parser computes HIGH confidence for rich response`() {
        val parser = DeepAnalysisResponseParserImpl()
        val result = parser.parse("T-1", RICH_JSON)
        assertEquals(ExtractionConfidence.HIGH, result.analysisMetadata.extractionConfidence)
    }

    @Test
    fun `parser computes LOW confidence for empty response`() {
        val parser = DeepAnalysisResponseParserImpl()
        val result = parser.parse("T-2", "{}")
        assertEquals(ExtractionConfidence.LOW, result.analysisMetadata.extractionConfidence)
    }
}

private val RICH_JSON = """
{
  "requirementSummary": {"unified": "Summary", "businessSummary": "Biz"},
  "evolution": [{"version": "v1", "date": "2024-01-01", "description": "Init"}],
  "complexity": {"scrumPoints": 5.0, "description": "Medium"},
  "technicalDetails": {
    "apiSpecifications": [{"method": "GET", "path": "/api/test", "description": "Test"}]
  },
  "acceptanceCriteria": [{"id": "AC1", "description": "Test"}],
  "dependencies": {"blockingIssues": [{"key": "T-10", "summary": "Blocker"}]}
}
""".trimIndent()
