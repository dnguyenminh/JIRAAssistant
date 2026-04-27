package com.assistant.frontend.pages.ticket.drawio

import com.assistant.ai.deepanalysis.models.DrawioMetadata

/**
 * Merges DrawioMetadata (nodes + connections) into an XML template.
 * Pipeline: load template → calculate positions → generate cells → inject.
 * Requirements: 4.1, 4.2, 4.3, 4.6
 */
internal object DrawioTemplateEngine {

    fun merge(metadata: DrawioMetadata, onReady: (String) -> Unit) {
        DrawioTemplateRegistry.load(metadata.template) { templateXml ->
            val xml = mergeSync(metadata, templateXml)
            onReady(xml)
        }
    }

    private fun mergeSync(metadata: DrawioMetadata, templateXml: String): String {
        val positions = DrawioLayoutEngine.calculate(metadata.template, metadata.nodes.size)
        val nodeIdMap = buildNodeIdMap(metadata)
        val nodeCells = generateNodeCells(metadata, positions, nodeIdMap)
        val edgeCells = generateEdgeCells(metadata, nodeIdMap)
        return injectCells(templateXml, nodeCells + edgeCells)
    }

    private fun buildNodeIdMap(metadata: DrawioMetadata): Map<String, Int> =
        metadata.nodes.mapIndexed { i, node -> node.id to (i + 2) }.toMap()

    private fun generateNodeCells(
        metadata: DrawioMetadata,
        positions: List<Pair<Int, Int>>,
        nodeIdMap: Map<String, Int>
    ): List<String> = metadata.nodes.mapIndexed { i, node ->
        val (x, y) = positions.getOrElse(i) { Pair(40, 40) }
        val cellId = nodeIdMap[node.id] ?: (i + 2)
        DrawioShapeMapper.toCell(node.id, node.label, node.type, x, y, cellId)
    }

    private fun generateEdgeCells(
        metadata: DrawioMetadata, nodeIdMap: Map<String, Int>
    ): List<String> {
        val baseId = metadata.nodes.size + 2
        return metadata.connections.mapIndexed { i, conn ->
            val src = nodeIdMap[conn.from] ?: return@mapIndexed ""
            val tgt = nodeIdMap[conn.to] ?: return@mapIndexed ""
            toEdgeCell(conn.label, baseId + i, src, tgt)
        }.filter { it.isNotEmpty() }
    }

    private fun toEdgeCell(label: String, cellId: Int, src: Int, tgt: Int): String {
        val escaped = label.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
        return "<mxCell id=\"$cellId\" value=\"$escaped\" edge=\"1\" " +
            "source=\"$src\" target=\"$tgt\" parent=\"1\">" +
            "<mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>"
    }

    private fun injectCells(templateXml: String, cells: List<String>): String {
        val marker = "</root>"
        val cellsXml = cells.joinToString("")
        return templateXml.replace(marker, "$cellsXml$marker")
    }
}
