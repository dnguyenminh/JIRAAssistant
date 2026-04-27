package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Draw.io metadata describing template, nodes, and connections for template-based diagram rendering.
 * AI generates this JSON metadata; frontend merges it into XML templates.
 * Requirements: 1.3
 */
@Serializable
data class DrawioMetadata(
    val template: String = "component",
    val nodes: List<DrawioNode> = emptyList(),
    val connections: List<DrawioConnection> = emptyList()
)

/**
 * A single node in a draw.io diagram.
 * Type maps to a draw.io shape via DrawioShapeMapper.
 * Requirements: 1.4
 */
@Serializable
data class DrawioNode(
    val id: String = "",
    val label: String = "",
    val type: String = ""
)

/**
 * A connection (edge) between two nodes in a draw.io diagram.
 * Requirements: 1.5
 */
@Serializable
data class DrawioConnection(
    val from: String = "",
    val to: String = "",
    val label: String = ""
)
