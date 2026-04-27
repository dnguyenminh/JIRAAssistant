package com.assistant.ai.deepanalysis

import com.assistant.ai.AnalysisSource
import com.assistant.ai.deepanalysis.models.ExtractionConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for DeepAnalysisResponseParserImpl.
 * Validates: Requirements 25.1, 25.2, 25.3
 */
class DeepAnalysisResponseParserTest {

    private val parser = DeepAnalysisResponseParserImpl()

    // ── Req 25.1: Parse valid JSON into AnalysisResult ──

    @Test
    fun `parse maps all fields from valid JSON response`() {
        val result = parser.parse("PROJ-1", FULL_RESPONSE_JSON)
        assertEquals("PROJ-1", result.ticketId)
        assertEquals("Unified summary", result.context.unified)
        assertEquals("Business overview", result.context.businessSummary)
        assertEquals("Old system", result.context.asIsState)
        assertEquals("New system", result.context.toBeState)
        assertEquals(listOf("Req A", "Req B"), result.context.extractedRequirements)
        assertEquals(1, result.context.affectedModules.size)
        assertEquals("Auth", result.context.affectedModules[0].name)
    }

    @Test
    fun `parse maps evolution entries correctly`() {
        val result = parser.parse("PROJ-1", FULL_RESPONSE_JSON)
        assertEquals(1, result.evolution.size)
        assertEquals("v1", result.evolution[0].version)
        assertEquals("2024-01-01", result.evolution[0].date)
        assertEquals("Initial", result.evolution[0].description)
    }

    @Test
    fun `parse maps complexity with normalized scrum points`() {
        val result = parser.parse("PROJ-1", FULL_RESPONSE_JSON)
        assertEquals(5.0, result.complexity.scrumPoints)
        assertEquals("Medium complexity", result.complexity.description)
        assertEquals(1, result.complexity.kbReferences.size)
        assertEquals("PROJ-99", result.complexity.kbReferences[0].ticketId)
    }

    @Test
    fun `parse maps technical details correctly`() {
        val result = parser.parse("PROJ-1", FULL_RESPONSE_JSON)
        assertEquals(1, result.technicalDetails.apiSpecifications.size)
        assertEquals("POST", result.technicalDetails.apiSpecifications[0].method)
        assertEquals(1, result.technicalDetails.databaseChanges.size)
        assertEquals("users", result.technicalDetails.databaseChanges[0].tableName)
    }

    @Test
    fun `parse sets source to FRESH_AI`() {
        val result = parser.parse("PROJ-1", FULL_RESPONSE_JSON)
        assertEquals(AnalysisSource.FRESH_AI, result.source)
    }

    // ── Req 25.2: Missing optional fields get defaults ──

    @Test
    fun `parse empty JSON object returns defaults`() {
        val result = parser.parse("PROJ-2", "{}")
        assertEquals("PROJ-2", result.ticketId)
        assertEquals("", result.context.unified)
        assertEquals("", result.context.businessSummary)
        assertTrue(result.context.affectedModules.isEmpty())
        assertTrue(result.evolution.isEmpty())
        assertEquals(0.0, result.complexity.scrumPoints)
        assertTrue(result.technicalDetails.apiSpecifications.isEmpty())
        assertTrue(result.acceptanceCriteria.isEmpty())
        assertTrue(result.dependencies.blockingIssues.isEmpty())
    }

    @Test
    fun `parse partial JSON fills missing fields with defaults`() {
        val json = """{"requirementSummary":{"unified":"Only summary"}}"""
        val result = parser.parse("PROJ-3", json)
        assertEquals("Only summary", result.context.unified)
        assertEquals("", result.context.businessSummary)
        assertTrue(result.evolution.isEmpty())
    }

    // ── Req 25.3: Invalid JSON throws DeepAnalysisParseException ──

    @Test
    fun `parse invalid JSON throws DeepAnalysisParseException`() {
        assertFailsWith<DeepAnalysisParseException> {
            parser.parse("PROJ-4", "not json at all")
        }
    }

    @Test
    fun `parse truncated JSON throws DeepAnalysisParseException`() {
        assertFailsWith<DeepAnalysisParseException> {
            parser.parse("PROJ-5", """{"requirementSummary":{"unified":""")
        }
    }

    @Test
    fun `parse empty string throws DeepAnalysisParseException`() {
        assertFailsWith<DeepAnalysisParseException> {
            parser.parse("PROJ-6", "")
        }
    }
}

/** Full valid AI response JSON for testing Req 25.1 */
private val FULL_RESPONSE_JSON = """
{
  "requirementSummary": {
    "unified": "Unified summary",
    "businessSummary": "Business overview",
    "asIsState": "Old system",
    "toBeState": "New system",
    "extractedRequirements": ["Req A", "Req B"],
    "affectedModules": [{"name": "Auth", "colorCategory": "PRIMARY"}]
  },
  "evolution": [
    {"version": "v1", "date": "2024-01-01", "description": "Initial", "changeType": "ORIGIN"}
  ],
  "complexity": {
    "scrumPoints": 5.0,
    "description": "Medium complexity",
    "kbReferences": [{"ticketId": "PROJ-99", "similarityPercent": 85.0}]
  },
  "technicalDetails": {
    "apiSpecifications": [{"method": "POST", "path": "/api/users", "description": "Create user"}],
    "databaseChanges": [{"tableName": "users", "operationType": "CREATE", "columns": ["id", "name"], "description": "User table"}],
    "externalIntegrations": [{"serviceName": "AuthService", "protocol": "REST", "endpoint": "/oauth", "description": "SSO"}]
  },
  "acceptanceCriteria": [
    {"id": "AC-1", "description": "User can login", "testabilityAssessment": "Automatable"}
  ],
  "dependencies": {
    "blockingIssues": [{"key": "PROJ-10", "summary": "Auth module", "relationshipType": "blocks", "riskLevel": "HIGH"}],
    "relatedIssues": [{"key": "PROJ-20", "summary": "Related", "relationshipType": "relates to", "riskLevel": "LOW"}],
    "externalDependencies": ["Redis"]
  },
  "analysisMetadata": {"extractionConfidence": "HIGH"}
}
""".trimIndent()
