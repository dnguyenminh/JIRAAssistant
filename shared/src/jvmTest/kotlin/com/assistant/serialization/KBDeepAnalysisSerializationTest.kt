package com.assistant.serialization

import com.assistant.ai.deepanalysis.models.*
import com.assistant.config.JsonConfig
import com.assistant.kb.KBDeepAnalysisData
import com.assistant.kb.KBRecord
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Serialization tests for KB deep analysis data structures.
 * Validates: Requirements 20.1-20.4
 */
class KBDeepAnalysisSerializationTest {

    private val json = JsonConfig.instance

    private fun arbAlpha(range: IntRange = 1..20): Arb<String> =
        Arb.string(range.first, range.last, Codepoint.alphanumeric())

    private fun arbDeepData(): Arb<KBDeepAnalysisData> = arbitrary {
        KBDeepAnalysisData(
            technicalDetails = TechnicalDetails(
                apiSpecifications = Arb.list(arbitrary {
                    ApiSpecification(
                        method = Arb.element("GET", "POST", "PUT").bind(),
                        path = "/api/${arbAlpha(3..8).bind()}"
                    )
                }, 0..2).bind()
            ),
            acceptanceCriteria = Arb.list(arbitrary {
                AcceptanceCriterion(
                    id = "AC-${Arb.int(1..99).bind()}",
                    description = arbAlpha(5..30).bind()
                )
            }, 0..3).bind(),
            dependencies = DependencyInfo(
                blockingIssues = Arb.list(arbitrary {
                    DependencyItem(key = "P-${Arb.int(1..999).bind()}")
                }, 0..2).bind()
            ),
            analysisMetadata = AnalysisMetadata(
                extractionConfidence = Arb.element(
                    ExtractionConfidence.HIGH,
                    ExtractionConfidence.MEDIUM,
                    ExtractionConfidence.LOW
                ).bind(),
                aiProviderUsed = Arb.element("gemini", "ollama").bind()
            ),
            businessSummary = arbAlpha(0..30).bind(),
            asIsState = arbAlpha(0..30).bind(),
            toBeState = arbAlpha(0..30).bind(),
            extractedRequirements = Arb.list(arbAlpha(5..20), 0..3).bind()
        )
    }

    /**
     * **Validates: Requirements 20.2, 20.3**
     * KBDeepAnalysisData round-trip: serialize → deserialize preserves all fields.
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `KBDeepAnalysisData serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbDeepData()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<KBDeepAnalysisData>(jsonStr)
            assertEquals(original, restored)
        }
    }

    /**
     * **Validates: Requirements 20.4**
     * Old KBRecord JSON missing deep analysis fields deserializes with defaults.
     */
    @Test
    fun `old KBRecord without deep analysis fields deserializes with defaults`() {
        val oldJson = """
            {
                "ticketId":"PROJ-1",
                "requirementSummary":"summary",
                "evolutionHistory":[],
                "scrumPoints":3.0,
                "confidenceScore":0.8,
                "rationale":"rationale",
                "similarTicketRefs":[],
                "timestamp":"2024-01-01T00:00:00Z"
            }
        """.trimIndent()
        val record = json.decodeFromString<KBRecord>(oldJson)
        assertEquals(TechnicalDetails(), record.technicalDetails)
        assertEquals(emptyList(), record.acceptanceCriteria)
        assertEquals(DependencyInfo(), record.dependencies)
        assertEquals(AnalysisMetadata(), record.analysisMetadata)
        assertEquals("", record.businessSummary)
        assertEquals("", record.asIsState)
        assertEquals("", record.toBeState)
        assertEquals(emptyList(), record.extractedRequirements)
    }

    /**
     * **Validates: Requirements 20.4**
     * Empty deep_analysis_json ('{}') deserializes to KBDeepAnalysisData with defaults.
     */
    @Test
    fun `empty deep_analysis_json deserializes to defaults`() {
        val data = json.decodeFromString<KBDeepAnalysisData>("{}")
        assertEquals(KBDeepAnalysisData(), data)
    }

    /**
     * **Validates: Requirements 20.4**
     * Partial deep_analysis_json with only some fields still deserializes.
     */
    @Test
    fun `partial deep_analysis_json deserializes missing fields with defaults`() {
        val partialJson = """{"businessSummary":"test summary"}"""
        val data = json.decodeFromString<KBDeepAnalysisData>(partialJson)
        assertEquals("test summary", data.businessSummary)
        assertEquals(TechnicalDetails(), data.technicalDetails)
        assertEquals(emptyList(), data.acceptanceCriteria)
        assertEquals(DependencyInfo(), data.dependencies)
        assertEquals(AnalysisMetadata(), data.analysisMetadata)
    }
}
