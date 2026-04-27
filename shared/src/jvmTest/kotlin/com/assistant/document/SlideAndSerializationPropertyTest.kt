package com.assistant.document

import com.assistant.document.models.GeneratedDocument
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 10: Slide generation structure.
 * Property 11: GeneratedDocument serialization round-trip.
 *
 * **Validates: Requirements 4.1, 4.2, 11.1, 11.2, 11.3**
 */
class SlideAndSerializationPropertyTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `Property 10 - slides have separators, max 7 slides, max 7 bullets each`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.brdMarkdown(sectionCount = 3..9)
        ) { markdown ->
            if (markdown.isBlank()) return@checkAll
            val slides = SlideGenerator.generate(markdown)
            val slideParts = slides.split("---").map { it.trim() }.filter { it.isNotBlank() }

            assertTrue(
                slideParts.size <= 7,
                "Expected ≤7 slides, got ${slideParts.size}"
            )
            slideParts.forEach { slide ->
                val bulletCount = slide.lines().count { it.trimStart().startsWith("- ") }
                assertTrue(
                    bulletCount <= 7,
                    "Slide has $bulletCount bullets, expected ≤7"
                )
            }
        }
    }

    @Test
    fun `Property 11 - GeneratedDocument serialize then deserialize equals original`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.generatedDocument()
        ) { doc ->
            val serialized = json.encodeToString(doc)
            val deserialized = json.decodeFromString<GeneratedDocument>(serialized)
            assertEquals(doc, deserialized, "Round-trip failed for $doc")
        }
    }
}
