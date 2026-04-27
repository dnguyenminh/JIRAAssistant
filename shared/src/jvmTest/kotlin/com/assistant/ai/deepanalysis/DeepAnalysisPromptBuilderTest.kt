package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit tests for DeepAnalysisPromptBuilderImpl.
 * Requirements: 18.1-18.6
 */
class DeepAnalysisPromptBuilderTest {

    private val builder = DeepAnalysisPromptBuilderImpl()

    private fun fullContent() = StructuredTicketContent(
        summary = "Implement OAuth login",
        status = "In Progress",
        priority = "High",
        issueType = "Story",
        assignee = "Alice",
        reporter = "Bob",
        storyPoints = 5.0,
        createdDate = "2024-01-01",
        updatedDate = "2024-01-15",
        labels = listOf("backend", "auth"),
        components = listOf("Auth Module"),
        description = "Full ticket description here",
        subTasks = listOf(SubTaskInfo(key = "SUB-1", summary = "Setup OAuth", status = "Done")),
        issueLinks = listOf(IssueLinkInfo("PROJ-50", "DB setup", "blocks")),
        attachments = listOf(AttachmentInfo(filename = "mockup.png", mimeType = "image/png", size = 1024)),
        comments = listOf(CommentInfo("Reviewer", "2024-01-10", "Looks good")),
        changelog = listOf(ChangelogEntry("status", "Open", "In Progress", "Admin", "2024-01-05")),
        classifiedContent = ClassifiedContent(
            asIsState = "Manual login",
            toBeState = "OAuth 2.0 login",
            acceptanceCriteria = listOf("User can log in via Google"),
            apiSpecifications = listOf(ApiSpecification("POST", "/api/auth/login", "Login endpoint")),
            databaseChanges = listOf(DatabaseChange("users", "ALTER", listOf("oauth_token"), "Add OAuth column"))
        )
    )

    // ── Req 18.1: Prompt includes all StructuredTicketContent data ──

    @Test
    fun `prompt includes summary status priority issueType`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("Implement OAuth login"), "summary")
        assertTrue(prompt.contains("In Progress"), "status")
        assertTrue(prompt.contains("High"), "priority")
        assertTrue(prompt.contains("Story"), "issueType")
    }

    @Test
    fun `prompt includes assignee reporter dates`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("Alice"), "assignee")
        assertTrue(prompt.contains("Bob"), "reporter")
        assertTrue(prompt.contains("2024-01-01"), "created")
        assertTrue(prompt.contains("2024-01-15"), "updated")
    }

    @Test
    fun `prompt includes labels components storyPoints`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("backend"), "label")
        assertTrue(prompt.contains("Auth Module"), "component")
        assertTrue(prompt.contains("5.0"), "storyPoints")
    }

    @Test
    fun `prompt includes description and classified sections`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("Full ticket description here"))
        assertTrue(prompt.contains("Manual login"), "asIs")
        assertTrue(prompt.contains("OAuth 2.0 login"), "toBe")
    }

    @Test
    fun `prompt includes subTasks issueLinks comments changelog attachments`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("Setup OAuth"), "subTask")
        assertTrue(prompt.contains("PROJ-50"), "issueLink")
        assertTrue(prompt.contains("Looks good"), "comment")
        assertTrue(prompt.contains("Open"), "changelog old")
        assertTrue(prompt.contains("mockup.png"), "attachment")
    }

    // ── Req 18.2: Prompt requests 6 analysis aspects ──

    @Test
    fun `prompt requests all 6 analysis aspects`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("Business requirement summary") ||
            prompt.contains("business requirement summary", ignoreCase = true))
        assertTrue(prompt.contains("Acceptance criteria") ||
            prompt.contains("acceptance criteria", ignoreCase = true))
        assertTrue(prompt.contains("Technical details") ||
            prompt.contains("technical details", ignoreCase = true))
        assertTrue(prompt.contains("Dependencies") ||
            prompt.contains("dependencies", ignoreCase = true))
        assertTrue(prompt.contains("Change history") ||
            prompt.contains("change history", ignoreCase = true))
        assertTrue(prompt.contains("Complexity assessment") ||
            prompt.contains("complexity assessment", ignoreCase = true))
    }

    // ── Req 18.3: API specs present → include + completeness assessment ──

    @Test
    fun `prompt includes API specs and completeness assessment`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("POST"))
        assertTrue(prompt.contains("/api/auth/login"))
        assertTrue(prompt.contains("completeness", ignoreCase = true))
    }

    @Test
    fun `prompt omits API section when no API specs`() {
        val content = fullContent().copy(
            classifiedContent = ClassifiedContent()
        )
        val prompt = builder.buildPrompt(content)
        assertTrue(!prompt.contains("API SPECIFICATIONS"))
    }

    // ── Req 18.4: DB changes present → include + data model assessment ──

    @Test
    fun `prompt includes DB changes and data model assessment`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("ALTER"))
        assertTrue(prompt.contains("users"))
        assertTrue(prompt.contains("oauth_token"))
        assertTrue(prompt.contains("data model", ignoreCase = true))
    }

    @Test
    fun `prompt omits DB section when no DB changes`() {
        val content = fullContent().copy(
            classifiedContent = ClassifiedContent(
                apiSpecifications = listOf(ApiSpecification("GET", "/api/test", "test"))
            )
        )
        val prompt = builder.buildPrompt(content)
        assertTrue(!prompt.contains("DATABASE CHANGES"))
    }

    // ── Req 18.5: Strict JSON output schema ──

    @Test
    fun `prompt includes JSON output schema`() {
        val prompt = builder.buildPrompt(fullContent())
        assertTrue(prompt.contains("JSON"), "mentions JSON")
        assertTrue(prompt.contains("requirementSummary"), "schema field")
        assertTrue(prompt.contains("scrumPoints"), "schema field")
        assertTrue(prompt.contains("technicalDetails"), "schema field")
    }

    // ── Req 18.6: Anti-hallucination instruction ──

    @Test
    fun `prompt includes anti-hallucination instruction`() {
        val prompt = builder.buildPrompt(fullContent())
        val lower = prompt.lowercase()
        assertTrue(lower.contains("do not fabricate") ||
            lower.contains("don't fabricate") ||
            lower.contains("not fabricate"))
        assertTrue(lower.contains("actual") || lower.contains("only"))
    }

    // ── Edge case: empty/minimal StructuredTicketContent ──

    @Test
    fun `empty content still produces valid prompt with analysis instructions`() {
        val prompt = builder.buildPrompt(StructuredTicketContent())
        assertTrue(prompt.contains("ANALYSIS INSTRUCTIONS") ||
            prompt.contains("analysis", ignoreCase = true))
        assertTrue(prompt.contains("JSON"), "JSON schema present")
        assertTrue(prompt.isNotBlank())
    }
}
