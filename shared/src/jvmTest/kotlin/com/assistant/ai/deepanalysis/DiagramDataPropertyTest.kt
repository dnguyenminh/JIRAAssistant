package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
import com.assistant.config.JsonConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for DiagramData models.
 *
 * Feature: drawio-template-diagrams, Property 1: DiagramData serialization round-trip
 * Feature: drawio-template-diagrams, Property 2: Mermaid format invariant
 */
class DiagramDataPropertyTest {

    private val json = JsonConfig.instance

    // ── Generators ──────────────────────────────────────────────────────

    private val validNodeTypes = listOf(
        "webapp", "database", "external_api", "server", "mobile",
        "cloud", "user", "service", "queue", "cache"
    )

    private val validTemplates = listOf(
        "flow", "deployment", "component", "dependency", "bpmn"
    )

    private fun arbAlpha(range: IntRange = 1..20): Arb<String> =
        Arb.string(range.first, range.last, Codepoint.alphanumeric())

    /** Random DrawioNode: random id (3-10 alphanumeric), label (1-50), type from valid set + 20% unknown */
    private fun arbDrawioNode(): Arb<DrawioNode> = arbitrary {
        val useUnknown = Arb.int(1..5).bind() == 1 // 20% chance
        DrawioNode(
            id = arbAlpha(3..10).bind(),
            label = arbAlpha(1..50).bind(),
            type = if (useUnknown) arbAlpha(3..8).bind() else Arb.element(validNodeTypes).bind()
        )
    }

    /** Random DrawioConnection: from/to from nodeIds + 10% chance invalid id, random label */
    private fun arbDrawioConnection(nodeIds: List<String>): Arb<DrawioConnection> = arbitrary {
        val useInvalidFrom = nodeIds.isNotEmpty() && Arb.int(1..10).bind() == 1
        val useInvalidTo = nodeIds.isNotEmpty() && Arb.int(1..10).bind() == 1
        DrawioConnection(
            from = if (useInvalidFrom || nodeIds.isEmpty()) arbAlpha(3..10).bind()
            else Arb.element(nodeIds).bind(),
            to = if (useInvalidTo || nodeIds.isEmpty()) arbAlpha(3..10).bind()
            else Arb.element(nodeIds).bind(),
            label = arbAlpha(0..30).bind()
        )
    }

    /** Random DrawioMetadata: random template, 1-20 nodes, 0-30 connections */
    private fun arbDrawioMetadata(): Arb<DrawioMetadata> = arbitrary {
        val nodes = Arb.list(arbDrawioNode(), 1..20).bind()
        val nodeIds = nodes.map { it.id }
        val connCount = Arb.int(0..30).bind()
        val connections = Arb.list(arbDrawioConnection(nodeIds), connCount..connCount).bind()
        DrawioMetadata(
            template = Arb.element(validTemplates + listOf("unknown_tpl")).bind(),
            nodes = nodes,
            connections = connections
        )
    }

    /** Random DiagramData with format "drawio" and random DrawioMetadata */
    private fun arbDrawioDiagramData(): Arb<DiagramData> = arbitrary {
        DiagramData(
            type = Arb.element("flow", "component", "dependency", "deployment", "bpmn").bind(),
            title = arbAlpha(1..40).bind(),
            mermaidCode = "",
            format = "drawio",
            drawioMetadata = arbDrawioMetadata().bind()
        )
    }

    /** Random DiagramData with format "mermaid" and random mermaidCode */
    private fun arbMermaidDiagramData(): Arb<DiagramData> = arbitrary {
        DiagramData(
            type = Arb.element("flow", "component", "dependency").bind(),
            title = arbAlpha(1..40).bind(),
            mermaidCode = "graph TD\n  A[${arbAlpha(2..15).bind()}] --> B[${arbAlpha(2..15).bind()}]",
            format = "mermaid",
            drawioMetadata = null
        )
    }

    // ── Property 1: DiagramData serialization round-trip ────────────────

    /**
     * **Validates: Requirements 1.6**
     *
     * For any valid DiagramData with format "drawio" and valid DrawioMetadata,
     * serialize to JSON then deserialize SHALL produce an equivalent object.
     *
     * Feature: drawio-template-diagrams, Property 1: DiagramData serialization round-trip
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 1 - DiagramData drawio serialization round-trip`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbDrawioDiagramData()) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<DiagramData>(jsonStr)
            assertEquals(original, restored, "DiagramData round-trip failed for: $original")
        }
    }

    // ── Property 2: Mermaid format invariant ────────────────────────────

    /**
     * **Validates: Requirements 1.7**
     *
     * For any valid DiagramData with format "mermaid",
     * drawioMetadata SHALL be null and mermaidCode SHALL be non-empty.
     *
     * Feature: drawio-template-diagrams, Property 2: Mermaid format invariant
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 2 - Mermaid format invariant`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbMermaidDiagramData()) { diagram ->
            assertEquals("mermaid", diagram.format, "Format should be mermaid")
            assertNull(diagram.drawioMetadata, "drawioMetadata should be null for mermaid format")
            assertTrue(diagram.mermaidCode.isNotEmpty(), "mermaidCode should not be empty for mermaid format")
        }
    }
}
