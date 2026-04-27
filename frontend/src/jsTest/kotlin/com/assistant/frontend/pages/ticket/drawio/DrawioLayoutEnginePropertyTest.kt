package com.assistant.frontend.pages.ticket.drawio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 5: Layout positions non-overlapping.
 *
 * For any list of nodes (1-20) and any template,
 * `DrawioLayoutEngine.calculate()` SHALL return positions
 * where no two nodes share the same (x, y) coordinates.
 *
 * **Validates: Requirements 4.4**
 *
 * Feature: drawio-template-diagrams, Property 5: Layout positions non-overlapping
 */
class DrawioLayoutEnginePropertyTest {

    private val validTemplates = listOf("flow", "deployment", "component", "dependency", "bpmn")

    @Test
    fun layoutPositionsAreUniqueForAllTemplatesAndCounts() {
        val rng = Random(seed = 55)
        repeat(120) { i ->
            val template = validTemplates[rng.nextInt(validTemplates.size)]
            val nodeCount = rng.nextInt(1, 21) // 1-20 nodes

            val positions = DrawioLayoutEngine.calculate(template, nodeCount)

            assertEquals(
                nodeCount,
                positions.size,
                "Iteration $i: template='$template', nodeCount=$nodeCount — should return exactly $nodeCount positions"
            )

            val uniquePositions = positions.toSet()
            assertEquals(
                positions.size,
                uniquePositions.size,
                "Iteration $i: template='$template', nodeCount=$nodeCount — " +
                    "all positions must be unique but found duplicates: " +
                    positions.groupBy { it }
                        .filter { it.value.size > 1 }
                        .keys.joinToString()
            )
        }
    }

    @Test
    fun emptyNodeListReturnsEmptyPositions() {
        for (template in validTemplates) {
            val positions = DrawioLayoutEngine.calculate(template, 0)
            assertTrue(
                positions.isEmpty(),
                "template='$template' with 0 nodes should return empty list"
            )
        }
    }

    @Test
    fun singleNodeAlwaysProducesOnePosition() {
        for (template in validTemplates) {
            val positions = DrawioLayoutEngine.calculate(template, 1)
            assertEquals(
                1,
                positions.size,
                "template='$template' with 1 node should return exactly 1 position"
            )
        }
    }

    @Test
    fun unknownTemplatePositionsAreAlsoUnique() {
        val rng = Random(seed = 99)
        repeat(120) { i ->
            val unknownTemplate = "unknown_${rng.nextInt(1000)}"
            val nodeCount = rng.nextInt(1, 21)

            val positions = DrawioLayoutEngine.calculate(unknownTemplate, nodeCount)

            assertEquals(nodeCount, positions.size,
                "Iteration $i: unknown template='$unknownTemplate' should still return $nodeCount positions")

            val uniquePositions = positions.toSet()
            assertEquals(
                positions.size,
                uniquePositions.size,
                "Iteration $i: unknown template='$unknownTemplate', nodeCount=$nodeCount — " +
                    "positions must be unique but found duplicates: " +
                    positions.groupBy { it }
                        .filter { it.value.size > 1 }
                        .keys.joinToString()
            )
        }
    }
}
