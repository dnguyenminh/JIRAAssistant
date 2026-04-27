package com.assistant.server.chat

import com.assistant.ai.deepanalysis.models.*
import com.assistant.kb.KBRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for ChatDeepAnalysisContext.
 * Requirements: 24.1-24.4
 */
class ChatDeepAnalysisContextTest {

    private fun baseRecord(ticketId: String = "PROJ-1") = KBRecord(
        ticketId = ticketId,
        requirementSummary = "Summary",
        evolutionHistory = emptyList(),
        scrumPoints = 5.0,
        confidenceScore = 0.9,
        rationale = "Rationale",
        similarTicketRefs = emptyList(),
        timestamp = "2024-01-01"
    )

    // --- hasDeepAnalysis ---

    @Test
    fun `hasDeepAnalysis returns false for record with no deep data`() {
        assertFalse(ChatDeepAnalysisContext.hasDeepAnalysis(baseRecord()))
    }

    @Test
    fun `hasDeepAnalysis returns true when extractedRequirements present`() {
        val r = baseRecord().copy(extractedRequirements = listOf("Req 1"))
        assertTrue(ChatDeepAnalysisContext.hasDeepAnalysis(r))
    }

    @Test
    fun `hasDeepAnalysis returns true when technicalDetails has API specs`() {
        val td = TechnicalDetails(apiSpecifications = listOf(ApiSpecification("GET", "/api/v1", "desc")))
        val r = baseRecord().copy(technicalDetails = td)
        assertTrue(ChatDeepAnalysisContext.hasDeepAnalysis(r))
    }

    @Test
    fun `hasDeepAnalysis returns true when businessSummary present`() {
        val r = baseRecord().copy(businessSummary = "Business context")
        assertTrue(ChatDeepAnalysisContext.hasDeepAnalysis(r))
    }

    // --- buildContext with no deep analysis (Req 24.4) ---

    @Test
    fun `buildContext suggests Analyze Ticket when no deep analysis`() {
        val result = ChatDeepAnalysisContext.buildContext(baseRecord())
        assertTrue(result.contains("No deep analysis available"))
        assertTrue(result.contains("Analyze Ticket"))
        assertTrue(result.contains("PROJ-1"))
    }

    // --- buildContext with deep analysis (Req 24.1) ---

    @Test
    fun `buildContext includes business summary and states`() {
        val r = baseRecord().copy(
            businessSummary = "Login feature",
            asIsState = "Manual login",
            toBeState = "SSO login"
        )
        val result = ChatDeepAnalysisContext.buildContext(r)
        assertTrue(result.contains("Business Summary: Login feature"))
        assertTrue(result.contains("As-Is State: Manual login"))
        assertTrue(result.contains("To-Be State: SSO login"))
    }

    // --- buildContext with requirements (Req 24.2) ---

    @Test
    fun `buildContext includes extracted requirements`() {
        val r = baseRecord().copy(extractedRequirements = listOf("User can login", "User can logout"))
        val result = ChatDeepAnalysisContext.buildContext(r)
        assertTrue(result.contains("Extracted Requirements:"))
        assertTrue(result.contains("1. User can login"))
        assertTrue(result.contains("2. User can logout"))
    }

    // --- buildContext with technical details (Req 24.3) ---

    @Test
    fun `buildContext includes API specs and DB changes`() {
        val td = TechnicalDetails(
            apiSpecifications = listOf(ApiSpecification("POST", "/api/auth", "Auth endpoint")),
            databaseChanges = listOf(DatabaseChange("users", "ALTER", listOf("sso_id"), "Add SSO column"))
        )
        val r = baseRecord().copy(technicalDetails = td)
        val result = ChatDeepAnalysisContext.buildContext(r)
        assertTrue(result.contains("API Specifications:"))
        assertTrue(result.contains("POST /api/auth: Auth endpoint"))
        assertTrue(result.contains("Database Changes:"))
        assertTrue(result.contains("ALTER users: Add SSO column"))
    }

    @Test
    fun `buildContext includes external integrations`() {
        val td = TechnicalDetails(
            externalIntegrations = listOf(ExternalIntegration("Okta", "SAML", "/sso", "SSO provider"))
        )
        val r = baseRecord().copy(technicalDetails = td)
        val result = ChatDeepAnalysisContext.buildContext(r)
        assertTrue(result.contains("External Integrations:"))
        assertTrue(result.contains("Okta (SAML): SSO provider"))
    }

    // --- buildContext with acceptance criteria ---

    @Test
    fun `buildContext includes acceptance criteria`() {
        val ac = listOf(AcceptanceCriterion("AC-1", "User can login via SSO", "HIGH"))
        val r = baseRecord().copy(
            businessSummary = "SSO",
            acceptanceCriteria = ac
        )
        val result = ChatDeepAnalysisContext.buildContext(r)
        assertTrue(result.contains("Acceptance Criteria:"))
        assertTrue(result.contains("[AC-1] User can login via SSO"))
        assertTrue(result.contains("Testability: HIGH"))
    }

    // --- buildContext with dependencies ---

    @Test
    fun `buildContext includes dependencies`() {
        val deps = DependencyInfo(
            blockingIssues = listOf(DependencyItem("PROJ-2", "Auth service", "blocks", "HIGH")),
            relatedIssues = listOf(DependencyItem("PROJ-3", "User mgmt", "relates", "LOW")),
            externalDependencies = listOf("Okta API")
        )
        val r = baseRecord().copy(businessSummary = "Feature", dependencies = deps)
        val result = ChatDeepAnalysisContext.buildContext(r)
        assertTrue(result.contains("Dependencies:"))
        assertTrue(result.contains("BLOCKING: PROJ-2"))
        assertTrue(result.contains("RELATED: PROJ-3"))
        assertTrue(result.contains("EXTERNAL: Okta API"))
    }
}
