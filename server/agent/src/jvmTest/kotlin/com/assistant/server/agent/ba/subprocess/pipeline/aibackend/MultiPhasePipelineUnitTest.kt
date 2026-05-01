package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.PipelineConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Multi-Phase BRD Pipeline feature.
 * Validates PipelineConfig defaults, BrdAssembler edge cases,
 * PhaseToolFilter patterns, and phase prompt content.
 */
class MultiPhasePipelineUnitTest {

    // ── 8.2 PipelineConfig defaults ─────────────────────────

    @Test
    fun `PipelineConfig default phase1MaxToolCalls is 25`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(25, config.phase1MaxToolCalls)
    }

    @Test
    fun `PipelineConfig default phase1TimeoutSeconds is 180`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(180, config.phase1TimeoutSeconds)
    }

    @Test
    fun `PipelineConfig default phase2MaxToolCalls is 15`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(15, config.phase2MaxToolCalls)
    }

    @Test
    fun `PipelineConfig default phase2TimeoutSeconds is 120`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(120, config.phase2TimeoutSeconds)
    }

    @Test
    fun `PipelineConfig default phase3MaxToolCalls is 10`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(10, config.phase3MaxToolCalls)
    }

    @Test
    fun `PipelineConfig default phase3TimeoutSeconds is 90`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(90, config.phase3TimeoutSeconds)
    }

    @Test
    fun `PipelineConfig default enableParallelPhases is true`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(true, config.enableParallelPhases)
    }

    @Test
    fun `PipelineConfig default maxRetries is 1`() {
        val config = PipelineConfig(ticketId = "T-1", docType = "BRD", allTools = emptyList())
        assertEquals(1, config.maxRetries)
    }

    // ── 8.3 BrdAssembler edge cases ─────────────────────────

    private val assembler = BrdAssembler()

    @Test
    fun `Assembly with null diagram output returns BRD as-is`() {
        val brd = "## Project Overview\nContent"
        assertEquals(brd, assembler.assemble(brd, null))
    }

    @Test
    fun `Assembly with empty diagram output returns BRD as-is`() {
        val brd = "## Project Overview\nContent"
        assertEquals(brd, assembler.assemble(brd, ""))
    }

    @Test
    fun `Assembly with blank diagram output returns BRD as-is`() {
        val brd = "## Project Overview\nContent"
        assertEquals(brd, assembler.assemble(brd, "   "))
    }

    @Test
    fun `Assembly with no placeholders in BRD returns BRD unchanged`() {
        val brd = "## Project Overview\nNo placeholders here"
        val diagrams = "<!-- DIAGRAM:PROCESS_FLOW -->\n```xml\n<mxGraphModel/>\n```"
        val result = assembler.assemble(brd, diagrams)
        // BRD has no placeholders, so diagrams have nothing to replace
        assertEquals(brd, result)
    }

    @Test
    fun `DIAGRAM_LABELS contains all 4 types`() {
        val labels = BrdAssembler.DIAGRAM_LABELS
        assertEquals(4, labels.size)
        assertTrue(labels.contains("PROCESS_FLOW"))
        assertTrue(labels.contains("ACTIVITY"))
        assertTrue(labels.contains("DATA_MODEL"))
        assertTrue(labels.contains("DEPLOYMENT"))
    }

    // ── 8.4 Phase 1 prompt KB memory protocol ───────────────

    private val phasePromptBuilder = PhasePromptBuilder()
    private val kbTools = listOf(
        ToolDescriptor("mcp_kb_kb_search", "KB search")
    )

    @Test
    fun `Phase 1 prompt contains KB memory protocol title format`() {
        val prompt = phasePromptBuilder.buildPhase1Prompt("NET-100", kbTools)
        assertTrue(
            prompt.contains("[BRD:NET-100]"),
            "Should contain KB title format"
        )
        assertTrue(
            prompt.contains("Collection Summary"),
            "Should contain collection summary reference"
        )
    }

    @Test
    fun `Phase 1 prompt contains KB tags instruction`() {
        val prompt = phasePromptBuilder.buildPhase1Prompt("NET-100", kbTools)
        assertTrue(
            prompt.contains("brd-pipeline,NET-100"),
            "Should contain KB tags"
        )
    }

    // ── 8.5 Phase 2 prompt diagram placeholders ─────────────

    @Test
    fun `Phase 2 prompt contains diagram placeholder markers`() {
        val prompt = phasePromptBuilder.buildPhase2Prompt("NET-100", "BRD", kbTools)
        assertTrue(
            prompt.contains("<!-- DIAGRAM:PROCESS_FLOW -->"),
            "Should contain PROCESS_FLOW placeholder"
        )
        assertTrue(
            prompt.contains("<!-- DIAGRAM:ACTIVITY -->"),
            "Should contain ACTIVITY placeholder"
        )
        assertTrue(
            prompt.contains("<!-- DIAGRAM:DATA_MODEL -->"),
            "Should contain DATA_MODEL placeholder"
        )
        assertTrue(
            prompt.contains("<!-- DIAGRAM:DEPLOYMENT -->"),
            "Should contain DEPLOYMENT placeholder"
        )
    }
}
