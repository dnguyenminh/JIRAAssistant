package com.assistant.frontend.pages.ticket.drawio

/**
 * Calculates (x, y) positions for draw.io nodes based on template layout strategy.
 * Requirements: 4.4
 */
internal object DrawioLayoutEngine {

    fun calculate(template: String, nodeCount: Int): List<Pair<Int, Int>> {
        if (nodeCount <= 0) return emptyList()
        return when (template) {
            "deployment" -> tieredVertical(nodeCount)
            "flow" -> horizontal(nodeCount, spacingX = 200)
            "component" -> grid(nodeCount)
            "dependency" -> leftToRight(nodeCount)
            "bpmn" -> horizontal(nodeCount, spacingX = 180)
            else -> grid(nodeCount)
        }
    }

    /** Deployment: 3 rows, spacing 200y, nodes spread horizontally 160x */
    private fun tieredVertical(count: Int): List<Pair<Int, Int>> {
        val rows = 3
        val perRow = distributePerRow(count, rows)
        val positions = mutableListOf<Pair<Int, Int>>()
        var idx = 0
        for (row in 0 until rows) {
            val n = perRow[row]
            for (col in 0 until n) {
                positions.add(Pair(col * 160 + 40, row * 200 + 40))
                idx++
                if (idx >= count) return positions
            }
        }
        return positions
    }

    /** Horizontal: nodes left-to-right with given spacing */
    private fun horizontal(count: Int, spacingX: Int): List<Pair<Int, Int>> =
        (0 until count).map { Pair(it * spacingX + 40, 40) }

    /** Grid: 3 columns, spacing 180x, 140y */
    private fun grid(count: Int): List<Pair<Int, Int>> =
        (0 until count).map { i ->
            Pair((i % 3) * 180 + 40, (i / 3) * 140 + 40)
        }

    /** Left-to-right: spacing 200x, 120y stagger */
    private fun leftToRight(count: Int): List<Pair<Int, Int>> =
        (0 until count).map { i ->
            Pair(i * 200 + 40, (i % 2) * 120 + 40)
        }

    private fun distributePerRow(total: Int, rows: Int): List<Int> {
        val base = total / rows
        val remainder = total % rows
        return (0 until rows).map { if (it < remainder) base + 1 else base }
    }
}
