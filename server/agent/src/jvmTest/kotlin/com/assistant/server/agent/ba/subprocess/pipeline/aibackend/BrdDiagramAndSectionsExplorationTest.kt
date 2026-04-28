package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bug condition exploration tests for brd-diagram-and-sections-fix.
 *
 * These tests encode the EXPECTED behavior (after fix).
 * They MUST FAIL on unfixed code — failure confirms the bugs exist.
 * After the fix is applied, they should PASS.
 *
 * **Validates: Requirements 1.1, 1.2, 1.5, 2.1, 2.3, 2.4**
 */
class BrdDiagramAndSectionsExplorationTest {

    // ── Bug 1: CDN Dependency ───────────────────────────

    @Test
    fun `Bug 1 - VIEWER_CDN should be local path not CDN`() {
        val sourceFile = File(
            "frontend/src/jsMain/kotlin/com/assistant/" +
                "frontend/pages/ticket/DrawioDiagramRenderer.kt"
        )
        assertTrue(sourceFile.exists(), "DrawioDiagramRenderer.kt should exist")
        val content = sourceFile.readText()
        assertTrue(
            content.contains("\"/js/viewer-static.min.js\""),
            "VIEWER_CDN should be local path '/js/viewer-static.min.js'"
        )
        assertFalse(
            content.contains("https://viewer.diagrams.net"),
            "VIEWER_CDN should NOT contain external CDN URL"
        )
    }

    // ── Bug 2: Weak Prompt Enforcement ──────────────────

    @Test
    fun `Bug 2 - appendBrdSections should prohibit placeholders`() {
        val output = buildString { appendBrdSections("BRD") }
        assertTrue(
            output.contains("NEVER", ignoreCase = true),
            "appendBrdSections should contain 'NEVER' prohibition"
        )
        assertTrue(
            output.contains("Insufficient data", ignoreCase = true),
            "appendBrdSections should mention 'Insufficient data'"
        )
    }

    @Test
    fun `Bug 2 - appendBrdSections should require sub-sections`() {
        val output = buildString { appendBrdSections("BRD") }
        val required = listOf(
            "Process Overview",
            "Functional Requirements",
            "Non-Functional Requirements",
            "Data Requirements"
        )
        required.forEach { sub ->
            assertTrue(
                output.contains(sub, ignoreCase = true),
                "Should require sub-section: $sub"
            )
        }
    }

    // ── Bug 2b: Phase 2 Task Section Names ──────────────

    @Test
    fun `Bug 2b - Phase 2 prompt lists all 7 section names`() {
        val builder = PhasePromptBuilder()
        val tools = listOf(
            ToolDescriptor("mcp_kb_kb_search", "KB search")
        )
        val prompt = builder.buildPhase2Prompt("TEST-1", "BRD", tools)
        val taskSection = prompt.substringAfter("## TASK")
        val sections = listOf(
            "Revision History",
            "Project Overview",
            "Common Project Acronyms",
            "Existing Processes",
            "Project Requirements",
            "Sign Off",
            "Appendix"
        )
        sections.forEach { section ->
            assertTrue(
                taskSection.contains(section, ignoreCase = true),
                "Phase 2 TASK should list section: $section"
            )
        }
    }
}
