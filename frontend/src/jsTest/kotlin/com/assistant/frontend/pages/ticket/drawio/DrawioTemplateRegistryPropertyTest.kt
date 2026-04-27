package com.assistant.frontend.pages.ticket.drawio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 3: Template fallback for unknown names.
 *
 * For any string NOT in the valid template names set
 * (flow, deployment, component, dependency, bpmn),
 * `DrawioTemplateRegistry.resolveTemplateName()` SHALL return "component"
 * (the generic fallback template).
 *
 * **Validates: Requirements 2.4**
 *
 * Feature: drawio-template-diagrams, Property 3: Template fallback for unknown names
 */
class DrawioTemplateRegistryPropertyTest {

    private val validNames = setOf("flow", "deployment", "component", "dependency", "bpmn")
    private val fallbackName = "component"

    private val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '-', ' ', '.')

    private fun randomString(rng: Random, maxLen: Int = 20): String {
        val len = rng.nextInt(1, maxLen + 1)
        return (1..len).map { chars[rng.nextInt(chars.size)] }
            .joinToString("")
    }

    /** Generate a random string guaranteed NOT in the valid names set. */
    private fun randomUnknownName(rng: Random): String {
        var candidate: String
        do {
            candidate = randomString(rng)
        } while (candidate in validNames)
        return candidate
    }

    @Test
    fun unknownNameResolvesToComponentFallback() {
        val rng = Random(seed = 31)
        repeat(120) { i ->
            val unknownName = randomUnknownName(rng)

            val resolved = DrawioTemplateRegistry.resolveTemplateName(unknownName)

            assertEquals(
                fallbackName,
                resolved,
                "Iteration $i: unknown name='$unknownName' should resolve to '$fallbackName'"
            )
        }
    }

    @Test
    fun validNamesResolveToThemselves() {
        for (name in validNames) {
            val resolved = DrawioTemplateRegistry.resolveTemplateName(name)
            assertEquals(
                name,
                resolved,
                "Valid name='$name' should resolve to itself"
            )
        }
    }

    @Test
    fun emptyStringResolvesToFallback() {
        val resolved = DrawioTemplateRegistry.resolveTemplateName("")
        assertEquals(
            fallbackName,
            resolved,
            "Empty string should resolve to '$fallbackName'"
        )
    }

    @Test
    fun caseSensitiveNamesResolveToFallback() {
        val rng = Random(seed = 42)
        val caseMutations = validNames.flatMap { name ->
            listOf(
                name.uppercase(),
                name.replaceFirstChar { it.uppercase() },
                name + " ",
                " " + name
            )
        }.filter { it !in validNames }

        for (mutation in caseMutations) {
            val resolved = DrawioTemplateRegistry.resolveTemplateName(mutation)
            assertEquals(
                fallbackName,
                resolved,
                "Case/whitespace mutation='$mutation' should resolve to '$fallbackName'"
            )
        }
    }
}
