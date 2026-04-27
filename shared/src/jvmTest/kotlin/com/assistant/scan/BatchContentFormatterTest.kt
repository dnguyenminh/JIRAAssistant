package com.assistant.scan

import com.assistant.ai.*
import com.assistant.ai.deepanalysis.models.CommentInfo
import com.assistant.ai.deepanalysis.models.SubTaskInfo
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for BatchContentFormatter: isPlaceholderResult() and formatStructuredContent().
 * Validates: Requirements 2.4
 */
class BatchContentFormatterTest {

    // --- isPlaceholderResult tests (Task 3.4) ---

    @Test
    fun `isPlaceholderResult returns true for ellipsis`() {
        val result = makeResult("PROJ-1", "...")
        assertTrue(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult returns true for Placeholder text`() {
        val result = makeResult("PROJ-2", "Placeholder requirement summary for PROJ-2")
        assertTrue(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult returns true for case-insensitive Placeholder`() {
        val result = makeResult("PROJ-3", "placeholder text here")
        assertTrue(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult returns true for short string under 10 chars`() {
        val result = makeResult("PROJ-4", "short")
        assertTrue(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult returns true for empty string`() {
        val result = makeResult("PROJ-5", "")
        assertTrue(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult returns false for real analysis text`() {
        val result = makeResult("PROJ-6", "This ticket implements user authentication with OAuth2 flow")
        assertFalse(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult returns false for text exactly 10 chars`() {
        val result = makeResult("PROJ-7", "1234567890")
        assertFalse(isPlaceholderResult(result))
    }

    @Test
    fun `isPlaceholderResult trims whitespace before checking`() {
        val result = makeResult("PROJ-8", "   ...   ")
        assertTrue(isPlaceholderResult(result))
    }

    // --- formatStructuredContent tests ---

    @Test
    fun `formatStructuredContent includes summary`() {
        val content = StructuredTicketContent(summary = "Add login page")
        val text = formatStructuredContent(content)
        assertTrue(text.contains("Summary: Add login page"))
    }

    @Test
    fun `formatStructuredContent includes description`() {
        val content = StructuredTicketContent(
            summary = "Login",
            description = "Implement OAuth2 login flow"
        )
        val text = formatStructuredContent(content)
        assertTrue(text.contains("Description: Implement OAuth2 login flow"))
    }

    @Test
    fun `formatStructuredContent includes sub-tasks`() {
        val content = StructuredTicketContent(
            summary = "Epic",
            subTasks = listOf(SubTaskInfo("SUB-1", "Design UI", "Done"))
        )
        val text = formatStructuredContent(content)
        assertTrue(text.contains("[Done] Design UI"))
    }

    @Test
    fun `formatStructuredContent includes comments`() {
        val content = StructuredTicketContent(
            summary = "Bug fix",
            comments = listOf(CommentInfo("alice", "2024-01-01", "Fixed in v2"))
        )
        val text = formatStructuredContent(content)
        assertTrue(text.contains("alice: Fixed in v2"))
    }

    @Test
    fun `formatStructuredContent caps at 3000 chars`() {
        val longDesc = "A".repeat(4000)
        val content = StructuredTicketContent(summary = "Test", description = longDesc)
        val text = formatStructuredContent(content)
        assertTrue(text.length <= 3000)
    }

    // --- Helper ---

    private fun makeResult(ticketId: String, unified: String) = AnalysisResult(
        ticketId = ticketId,
        context = RequirementSummary(unified = unified),
        evolution = emptyList(),
        complexity = ComplexityAssessment(3.0, "Medium", emptyList()),
        source = AnalysisSource.FRESH_AI
    )
}
