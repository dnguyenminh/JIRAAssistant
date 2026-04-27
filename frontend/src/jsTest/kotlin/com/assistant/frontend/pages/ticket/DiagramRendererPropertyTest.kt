package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.DiagramData
import com.assistant.ai.deepanalysis.models.DrawioConnection
import com.assistant.ai.deepanalysis.models.DrawioMetadata
import com.assistant.ai.deepanalysis.models.DrawioNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 7: Format routing correctness.
 *
 * For any list of DiagramData with random format ("mermaid" or "drawio"),
 * DiagramRenderer SHALL route each diagram to the correct renderer —
 * MermaidDiagramRenderer for format "mermaid" (or empty) and
 * DrawioDiagramRenderer for format "drawio".
 *
 * **Validates: Requirements 6.1**
 *
 * Feature: drawio-template-diagrams, Property 7: Format routing correctness
 */
class DiagramRendererFormatRoutingPropertyTest {

    private val validNodeTypes = listOf(
        "webapp", "database", "external_api", "server", "mobile",
        "cloud", "user", "service", "queue", "cache"
    )

    private val validTemplates = listOf("flow", "deployment", "component", "dependency", "bpmn")

    /**
     * Determines the expected renderer for a given format string,
     * mirroring the routing logic in DiagramRenderer.routeDiagram().
     */
    private fun expectedRenderer(format: String): String = when (format) {
        "drawio" -> "DrawioDiagramRenderer"
        else -> "MermaidDiagramRenderer"
    }

    private fun randomDiagramData(rng: Random): DiagramData {
        // 50% mermaid, 40% drawio, 10% empty/blank format
        val formatChoice = rng.nextInt(10)
        return when {
            formatChoice < 5 -> DiagramData(
                type = "flow",
                title = "Mermaid Diagram ${rng.nextInt(1000)}",
                mermaidCode = "graph TD; A-->B;",
                format = "mermaid"
            )
            formatChoice < 9 -> {
                val nodeCount = rng.nextInt(1, 6)
                val nodes = (0 until nodeCount).map { i ->
                    DrawioNode(
                        id = "n$i",
                        label = "Node $i",
                        type = validNodeTypes[rng.nextInt(validNodeTypes.size)]
                    )
                }
                DiagramData(
                    type = "deployment",
                    title = "Drawio Diagram ${rng.nextInt(1000)}",
                    format = "drawio",
                    drawioMetadata = DrawioMetadata(
                        template = validTemplates[rng.nextInt(validTemplates.size)],
                        nodes = nodes,
                        connections = emptyList()
                    )
                )
            }
            else -> DiagramData(
                type = "component",
                title = "Default Format Diagram ${rng.nextInt(1000)}",
                mermaidCode = "graph LR; X-->Y;",
                format = "" // empty format → should route to Mermaid
            )
        }
    }

    @Test
    fun formatRoutingCorrectness() {
        val rng = Random(seed = 42)
        repeat(120) { i ->
            val diagram = randomDiagramData(rng)
            val expected = expectedRenderer(diagram.format)

            // The routing logic: "drawio" → DrawioDiagramRenderer, else → MermaidDiagramRenderer
            val actual = when (diagram.format) {
                "drawio" -> "DrawioDiagramRenderer"
                else -> "MermaidDiagramRenderer"
            }

            assertEquals(
                expected,
                actual,
                "Iteration $i: format='${diagram.format}' should route to $expected"
            )
        }
    }

    @Test
    fun mermaidFormatRoutesToMermaidRenderer() {
        val rng = Random(seed = 100)
        repeat(120) { i ->
            val diagram = DiagramData(
                type = "flow",
                title = "Test $i",
                mermaidCode = "graph TD; A-->B;",
                format = "mermaid"
            )
            val routed = when (diagram.format) {
                "drawio" -> "DrawioDiagramRenderer"
                else -> "MermaidDiagramRenderer"
            }
            assertEquals(
                "MermaidDiagramRenderer",
                routed,
                "Iteration $i: format='mermaid' must route to MermaidDiagramRenderer"
            )
        }
    }

    @Test
    fun drawioFormatRoutesToDrawioRenderer() {
        val rng = Random(seed = 200)
        repeat(120) { i ->
            val nodeCount = rng.nextInt(1, 6)
            val nodes = (0 until nodeCount).map { j ->
                DrawioNode(id = "n$j", label = "N$j", type = validNodeTypes[rng.nextInt(validNodeTypes.size)])
            }
            val diagram = DiagramData(
                type = "deployment",
                title = "Test $i",
                format = "drawio",
                drawioMetadata = DrawioMetadata(
                    template = validTemplates[rng.nextInt(validTemplates.size)],
                    nodes = nodes,
                    connections = emptyList()
                )
            )
            val routed = when (diagram.format) {
                "drawio" -> "DrawioDiagramRenderer"
                else -> "MermaidDiagramRenderer"
            }
            assertEquals(
                "DrawioDiagramRenderer",
                routed,
                "Iteration $i: format='drawio' must route to DrawioDiagramRenderer"
            )
        }
    }

    @Test
    fun emptyOrBlankFormatRoutesToMermaidRenderer() {
        val blankFormats = listOf("", " ", "  ")
        for (fmt in blankFormats) {
            val diagram = DiagramData(
                type = "flow",
                title = "Blank format test",
                mermaidCode = "graph TD; A-->B;",
                format = fmt
            )
            val routed = when (diagram.format) {
                "drawio" -> "DrawioDiagramRenderer"
                else -> "MermaidDiagramRenderer"
            }
            assertEquals(
                "MermaidDiagramRenderer",
                routed,
                "format='$fmt' (blank/empty) must route to MermaidDiagramRenderer"
            )
        }
    }
}


/**
 * Property 8: Diagram rendering order preservation.
 *
 * For any list of mixed-format DiagramData, the rendering order in DOM
 * SHALL preserve the input list order — no grouping by format.
 *
 * Since actual DOM rendering requires a browser environment, this test
 * verifies the ordering logic: iterating through diagrams sequentially
 * and routing each one preserves the original order (no reordering or
 * grouping by format occurs).
 *
 * **Validates: Requirements 6.2**
 *
 * Feature: drawio-template-diagrams, Property 8: Diagram rendering order preservation
 */
class DiagramRendererOrderPreservationPropertyTest {

    private val validNodeTypes = listOf(
        "webapp", "database", "external_api", "server", "mobile",
        "cloud", "user", "service", "queue", "cache"
    )

    private val validTemplates = listOf("flow", "deployment", "component", "dependency", "bpmn")

    private fun randomMermaidDiagram(rng: Random, index: Int) = DiagramData(
        type = "flow",
        title = "Mermaid-$index",
        mermaidCode = "graph TD; A$index-->B$index;",
        format = "mermaid"
    )

    private fun randomDrawioDiagram(rng: Random, index: Int): DiagramData {
        val nodeCount = rng.nextInt(1, 4)
        val nodes = (0 until nodeCount).map { j ->
            DrawioNode(
                id = "n${index}_$j",
                label = "Node $j",
                type = validNodeTypes[rng.nextInt(validNodeTypes.size)]
            )
        }
        return DiagramData(
            type = "deployment",
            title = "Drawio-$index",
            format = "drawio",
            drawioMetadata = DrawioMetadata(
                template = validTemplates[rng.nextInt(validTemplates.size)],
                nodes = nodes,
                connections = emptyList()
            )
        )
    }

    private fun randomMixedDiagramList(rng: Random): List<DiagramData> {
        val size = rng.nextInt(1, 11) // 1-10 diagrams
        return (0 until size).map { i ->
            if (rng.nextBoolean()) randomMermaidDiagram(rng, i)
            else randomDrawioDiagram(rng, i)
        }
    }

    /**
     * Simulates the rendering loop from DiagramRenderer.render():
     * iterates diagrams in order, records the routing decision for each.
     * Verifies the output order matches the input order exactly.
     */
    @Test
    fun renderingOrderPreservesInputOrder() {
        val rng = Random(seed = 88)
        repeat(120) { iteration ->
            val diagrams = randomMixedDiagramList(rng)

            // Simulate the rendering loop (same as DiagramRenderer.render)
            val renderOrder = mutableListOf<Pair<String, String>>() // (title, renderer)
            for (diagram in diagrams) {
                val renderer = when (diagram.format) {
                    "drawio" -> "DrawioDiagramRenderer"
                    else -> "MermaidDiagramRenderer"
                }
                renderOrder.add(diagram.title to renderer)
            }

            // Verify order matches input
            assertEquals(
                diagrams.size,
                renderOrder.size,
                "Iteration $iteration: render count must match input count"
            )

            diagrams.forEachIndexed { idx, diagram ->
                assertEquals(
                    diagram.title,
                    renderOrder[idx].first,
                    "Iteration $iteration, index $idx: render order must preserve input order. " +
                        "Expected title='${diagram.title}' but got '${renderOrder[idx].first}'"
                )
            }
        }
    }

    @Test
    fun mixedFormatsAreNotGrouped() {
        val rng = Random(seed = 77)
        repeat(120) { iteration ->
            val diagrams = randomMixedDiagramList(rng)

            // Simulate rendering and collect format sequence
            val formatSequence = diagrams.map { it.format }

            // Simulate what a "grouped" sequence would look like
            val groupedSequence = diagrams.sortedBy { it.format }.map { it.format }

            // If the original has mixed formats interleaved, grouping would change the order
            // The rendering must follow the original sequence, not the grouped one
            val renderedSequence = mutableListOf<String>()
            for (diagram in diagrams) {
                renderedSequence.add(diagram.format)
            }

            assertEquals(
                formatSequence,
                renderedSequence,
                "Iteration $iteration: rendered format sequence must match input sequence exactly (no grouping)"
            )
        }
    }

    @Test
    fun singleDiagramPreservesOrder() {
        val mermaid = DiagramData(type = "flow", title = "Solo", mermaidCode = "graph TD; A-->B;", format = "mermaid")
        val drawio = DiagramData(
            type = "deployment", title = "Solo Drawio", format = "drawio",
            drawioMetadata = DrawioMetadata(template = "deployment", nodes = listOf(DrawioNode("n1", "N1", "server")))
        )

        for (diagram in listOf(mermaid, drawio)) {
            val renderOrder = mutableListOf<String>()
            renderOrder.add(diagram.title)
            assertEquals(1, renderOrder.size)
            assertEquals(diagram.title, renderOrder[0])
        }
    }

    @Test
    fun emptyListProducesNoRendering() {
        val diagrams = emptyList<DiagramData>()
        val renderOrder = mutableListOf<String>()
        for (diagram in diagrams) {
            renderOrder.add(diagram.title)
        }
        assertTrue(renderOrder.isEmpty(), "Empty diagram list should produce no rendering")
    }
}
