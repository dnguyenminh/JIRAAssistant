package com.assistant.kb

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

/**
 * Property-based test for KB storage round-trip with draw.io diagrams.
 *
 * Feature: drawio-template-diagrams, Property 10: KB storage round-trip for draw.io diagrams
 */
class KBDeepAnalysisDrawioPropertyTest {

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

    private fun arbDrawioNode(): Arb<DrawioNode> = arbitrary {
        val useUnknown = Arb.int(1..5).bind() == 1
        DrawioNode(
            id = arbAlpha(3..10).bind(),
            label = arbAlpha(1..50).bind(),
            type = if (useUnknown) arbAlpha(3..8).bind()
            else Arb.element(validNodeTypes).bind()
        )
    }

    private fun arbDrawioConnection(nodeIds: List<String>): Arb<DrawioConnection> = arbitrary {
        DrawioConnection(
            from = if (nodeIds.isEmpty()) arbAlpha(3..10).bind()
            else Arb.element(nodeIds).bind(),
            to = if (nodeIds.isEmpty()) arbAlpha(3..10).bind()
            else Arb.element(nodeIds).bind(),
            label = arbAlpha(0..30).bind()
        )
    }

    private fun arbDrawioMetadata(): Arb<DrawioMetadata> = arbitrary {
        val nodes = Arb.list(arbDrawioNode(), 1..10).bind()
        val nodeIds = nodes.map { it.id }
        val connCount = Arb.int(0..15).bind()
        val connections = Arb.list(
            arbDrawioConnection(nodeIds), connCount..connCount
        ).bind()
        DrawioMetadata(
            template = Arb.element(validTemplates).bind(),
            nodes = nodes,
            connections = connections
        )
    }

    private fun arbDrawioDiagramData(): Arb<DiagramData> = arbitrary {
        DiagramData(
            type = Arb.element(
                "flow", "component", "dependency", "deployment", "bpmn"
            ).bind(),
            title = arbAlpha(1..40).bind(),
            mermaidCode = "",
            format = "drawio",
            drawioMetadata = arbDrawioMetadata().bind()
        )
    }

    private fun arbMermaidDiagramData(): Arb<DiagramData> = arbitrary {
        DiagramData(
            type = Arb.element("flow", "component", "dependency").bind(),
            title = arbAlpha(1..40).bind(),
            mermaidCode = "graph TD\n  A --> B",
            format = "mermaid",
            drawioMetadata = null
        )
    }

    /** Mixed-format diagram: 50% mermaid, 50% drawio */
    private fun arbMixedDiagramData(): Arb<DiagramData> = arbitrary {
        if (Arb.boolean().bind()) arbDrawioDiagramData().bind()
        else arbMermaidDiagramData().bind()
    }

    /** Random KBDeepAnalysisData: 0-5 diagrams mixed format */
    private fun arbKbDeepAnalysisData(): Arb<KBDeepAnalysisData> = arbitrary {
        KBDeepAnalysisData(
            businessSummary = arbAlpha(0..30).bind(),
            diagrams = Arb.list(arbMixedDiagramData(), 0..5).bind()
        )
    }

    // ── Property 10: KB storage round-trip ──────────────────────────────

    /**
     * **Validates: Requirements 8.1**
     *
     * For any valid KBDeepAnalysisData containing a list of DiagramData
     * with mixed formats (mermaid + drawio), serialize to JSON then
     * deserialize SHALL produce an equivalent object — ensuring draw.io
     * metadata is not lost during KB storage.
     *
     * Feature: drawio-template-diagrams, Property 10: KB storage round-trip for draw.io diagrams
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 10 - KB storage round-trip for draw_io diagrams`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbKbDeepAnalysisData()
        ) { original ->
            val jsonStr = json.encodeToString(original)
            val restored = json.decodeFromString<KBDeepAnalysisData>(jsonStr)
            assertEquals(
                original, restored,
                "KBDeepAnalysisData round-trip failed"
            )
        }
    }
}
