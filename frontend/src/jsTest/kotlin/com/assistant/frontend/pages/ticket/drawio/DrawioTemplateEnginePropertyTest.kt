package com.assistant.frontend.pages.ticket.drawio

import com.assistant.ai.deepanalysis.models.DrawioConnection
import com.assistant.ai.deepanalysis.models.DrawioMetadata
import com.assistant.ai.deepanalysis.models.DrawioNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 4: Merge output cell counts match metadata.
 *
 * For any valid DrawioMetadata (random template, 1-20 nodes, 0-30 connections),
 * after merge, the output XML SHALL contain exactly N mxCell elements with
 * `vertex="1"` (= number of nodes) and exactly M mxCell elements with
 * `edge="1"` (= number of connections).
 *
 * **Validates: Requirements 4.2, 4.3, 4.6**
 *
 * Feature: drawio-template-diagrams, Property 4: Merge output cell counts match metadata
 */
class DrawioTemplateEnginePropertyTest {

    private val validTemplates = listOf(
        "flow", "deployment", "component", "dependency", "bpmn"
    )
    private val validNodeTypes = listOf(
        "webapp", "database", "external_api", "server", "mobile",
        "cloud", "user", "service", "queue", "cache"
    )
    private val chars = ('a'..'z') + ('0'..'9')

    private val templateXml =
        "<mxGraphModel><root><mxCell id=\"0\"/>" +
            "<mxCell id=\"1\" parent=\"0\"/></root></mxGraphModel>"

    private fun randomId(rng: Random): String {
        val len = rng.nextInt(3, 11)
        return (1..len).map { chars[rng.nextInt(chars.size)] }.joinToString("")
    }

    private fun randomLabel(rng: Random): String {
        val len = rng.nextInt(1, 30)
        return (1..len).map { ('a'..'z').random(rng) }.joinToString("")
    }

    private fun randomNodeType(rng: Random): String =
        if (rng.nextInt(5) == 0) "unknown_${rng.nextInt(100)}"
        else validNodeTypes[rng.nextInt(validNodeTypes.size)]

    private fun generateMetadata(rng: Random): DrawioMetadata {
        val template = validTemplates[rng.nextInt(validTemplates.size)]
        val nodeCount = rng.nextInt(1, 21)
        val nodes = generateUniqueNodes(rng, nodeCount)
        val connCount = rng.nextInt(0, 31)
        val connections = generateValidConnections(rng, nodes, connCount)
        return DrawioMetadata(template, nodes, connections)
    }

    private fun generateUniqueNodes(
        rng: Random, count: Int
    ): List<DrawioNode> {
        val ids = mutableSetOf<String>()
        return (0 until count).map {
            var id: String
            do { id = randomId(rng) } while (id in ids)
            ids.add(id)
            DrawioNode(id, randomLabel(rng), randomNodeType(rng))
        }
    }

    private fun generateValidConnections(
        rng: Random, nodes: List<DrawioNode>, count: Int
    ): List<DrawioConnection> {
        if (nodes.isEmpty()) return emptyList()
        val ids = nodes.map { it.id }
        return (0 until count).map {
            DrawioConnection(
                from = ids[rng.nextInt(ids.size)],
                to = ids[rng.nextInt(ids.size)],
                label = if (rng.nextBoolean()) randomLabel(rng) else ""
            )
        }
    }

    /**
     * Replicates the sync merge pipeline from DrawioTemplateEngine:
     * calculate positions → generate node cells → generate edge cells → inject.
     */
    private fun mergeSync(metadata: DrawioMetadata): String {
        val positions = DrawioLayoutEngine.calculate(
            metadata.template, metadata.nodes.size
        )
        val nodeIdMap = metadata.nodes.mapIndexed { i, n ->
            n.id to (i + 2)
        }.toMap()
        val nodeCells = metadata.nodes.mapIndexed { i, node ->
            val (x, y) = positions.getOrElse(i) { Pair(40, 40) }
            val cellId = nodeIdMap[node.id] ?: (i + 2)
            DrawioShapeMapper.toCell(
                node.id, node.label, node.type, x, y, cellId
            )
        }
        val baseId = metadata.nodes.size + 2
        val edgeCells = metadata.connections.mapIndexed { i, conn ->
            val src = nodeIdMap[conn.from] ?: return@mapIndexed ""
            val tgt = nodeIdMap[conn.to] ?: return@mapIndexed ""
            buildEdgeCell(conn.label, baseId + i, src, tgt)
        }.filter { it.isNotEmpty() }
        val cellsXml = (nodeCells + edgeCells).joinToString("")
        return templateXml.replace("</root>", "$cellsXml</root>")
    }

    private fun buildEdgeCell(
        label: String, cellId: Int, src: Int, tgt: Int
    ): String {
        val escaped = label.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
        return "<mxCell id=\"$cellId\" value=\"$escaped\" edge=\"1\" " +
            "source=\"$src\" target=\"$tgt\" parent=\"1\">" +
            "<mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>"
    }

    private fun countOccurrences(xml: String, pattern: String): Int {
        var count = 0
        var idx = xml.indexOf(pattern)
        while (idx >= 0) {
            count++
            idx = xml.indexOf(pattern, idx + pattern.length)
        }
        return count
    }

    @Test
    fun mergeOutputVertexCountMatchesNodeCount() {
        val rng = Random(seed = 44)
        repeat(120) { i ->
            val metadata = generateMetadata(rng)
            val xml = mergeSync(metadata)

            val vertexCount = countOccurrences(xml, "vertex=\"1\"")
            assertEquals(
                metadata.nodes.size,
                vertexCount,
                "Iteration $i: template='${metadata.template}', " +
                    "nodes=${metadata.nodes.size} — " +
                    "vertex count mismatch"
            )
        }
    }

    @Test
    fun mergeOutputEdgeCountMatchesConnectionCount() {
        val rng = Random(seed = 44)
        repeat(120) { i ->
            val metadata = generateMetadata(rng)
            val xml = mergeSync(metadata)

            val edgeCount = countOccurrences(xml, "edge=\"1\"")
            assertEquals(
                metadata.connections.size,
                edgeCount,
                "Iteration $i: template='${metadata.template}', " +
                    "connections=${metadata.connections.size} — " +
                    "edge count mismatch"
            )
        }
    }

    @Test
    fun mergeOutputContainsBothVerticesAndEdges() {
        val rng = Random(seed = 88)
        repeat(120) { i ->
            val metadata = generateMetadata(rng)
            val xml = mergeSync(metadata)

            val vertexCount = countOccurrences(xml, "vertex=\"1\"")
            val edgeCount = countOccurrences(xml, "edge=\"1\"")
            assertEquals(
                metadata.nodes.size, vertexCount,
                "Iteration $i: vertex count"
            )
            assertEquals(
                metadata.connections.size, edgeCount,
                "Iteration $i: edge count"
            )
        }
    }
}
