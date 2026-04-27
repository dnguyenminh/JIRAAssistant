package com.assistant.frontend.pages.ticket.drawio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property 6: Unknown node type fallback to default shape.
 *
 * For any node type string NOT in the valid set
 * (webapp, database, external_api, server, mobile, cloud, user, service, queue, cache),
 * `DrawioShapeMapper.toCell()` SHALL use the default rectangle style
 * instead of throwing an error.
 *
 * **Validates: Requirements 4.5**
 *
 * Feature: drawio-template-diagrams, Property 6: Unknown node type fallback to default shape
 */
class DrawioShapeMapperPropertyTest {

    private val validTypes = setOf(
        "webapp", "database", "external_api", "server", "mobile",
        "cloud", "user", "service", "queue", "cache"
    )

    private val defaultStyle =
        "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;"

    private val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '-')

    private fun randomString(rng: Random, maxLen: Int = 15): String {
        val len = rng.nextInt(1, maxLen + 1)
        return (1..len).map { chars[rng.nextInt(chars.size)] }
            .joinToString("")
    }

    /** Generate a random type string guaranteed NOT in the valid set. */
    private fun randomUnknownType(rng: Random): String {
        var candidate: String
        do {
            candidate = randomString(rng)
        } while (candidate in validTypes)
        return candidate
    }

    @Test
    fun unknownTypeUsesDefaultRectangleStyle() {
        val rng = Random(seed = 42)
        repeat(120) { i ->
            val unknownType = randomUnknownType(rng)
            val id = "node-$i"
            val label = "Label $i"
            val x = rng.nextInt(0, 1000)
            val y = rng.nextInt(0, 1000)
            val cellId = i + 2

            val xml = DrawioShapeMapper.toCell(id, label, unknownType, x, y, cellId)

            assertTrue(
                xml.contains("style=\"$defaultStyle\""),
                "Iteration $i: unknown type='$unknownType' should use default style"
            )
            assertTrue(
                xml.contains("vertex=\"1\""),
                "Iteration $i: output should contain vertex=\"1\""
            )
            assertFalse(
                xml.isEmpty(),
                "Iteration $i: output should not be empty"
            )
        }
    }

    @Test
    fun unknownTypeNeverThrowsError() {
        val rng = Random(seed = 77)
        repeat(120) { i ->
            val unknownType = randomUnknownType(rng)

            // Must not throw — just returns valid XML with default style
            val xml = DrawioShapeMapper.toCell(
                "n$i", "Test", unknownType, 0, 0, i + 2
            )
            assertTrue(xml.isNotEmpty(), "Iteration $i: should produce non-empty XML")
        }
    }

    @Test
    fun validTypesDoNotUseDefaultStyle() {
        for (type in validTypes) {
            val xml = DrawioShapeMapper.toCell("id", "Lbl", type, 0, 0, 2)
            val style = DrawioShapeMapper.styleFor(type)

            assertTrue(
                xml.contains("style=\"$style\""),
                "Valid type='$type' should use its own style, not default"
            )
        }
    }
}
