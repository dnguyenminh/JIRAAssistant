package com.assistant.server.attachment

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 3: Text chunking preserves content and respects size limit
 * For any non-blank text, joining all chunks must reconstruct the original words,
 * and each chunk's estimated token count must not exceed maxTokens (with tolerance).
 * Validates: Requirements 22.10
 */
@OptIn(ExperimentalKotest::class)
class TextChunkerPropertyTest {

    private val arbText: Arb<String> = Arb.bind(
        Arb.int(10..500),
        Arb.string(minSize = 2, maxSize = 8, codepoints = Codepoint.alphanumeric())
    ) { wordCount, seed ->
        (1..wordCount).joinToString(" ") { "${seed}$it" }
    }

    private val arbMaxTokens: Arb<Int> = Arb.int(20..500)

    @Test
    fun `all original words preserved across chunks`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbText) { text ->
                val chunks = TextChunker.chunk(text, maxTokens = 100)
                val originalWords = text.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
                val chunkWords = chunks.flatMap {
                    it.text.split(Regex("\\s+")).filter { w -> w.isNotBlank() }
                }.toSet()
                assertTrue(
                    originalWords.all { it in chunkWords },
                    "Some words lost during chunking"
                )
            }
        }
    }

    @Test
    fun `chunk indices are sequential starting from 0`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbText, arbMaxTokens) { text, maxTokens ->
                val chunks = TextChunker.chunk(text, maxTokens = maxTokens)
                chunks.forEachIndexed { i, chunk ->
                    assertEquals(i, chunk.index, "Chunk index must be sequential")
                }
            }
        }
    }

    @Test
    fun `each chunk has positive token count`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbText, arbMaxTokens) { text, maxTokens ->
                val chunks = TextChunker.chunk(text, maxTokens = maxTokens)
                assertTrue(chunks.isNotEmpty(), "Non-blank text must produce chunks")
                for (chunk in chunks) {
                    assertTrue(chunk.tokenCount > 0, "Token count must be positive")
                }
            }
        }
    }

    @Test
    fun `chunk word count respects maxTokens with tolerance`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbText, arbMaxTokens) { text, maxTokens ->
                val maxWords = (maxTokens * 0.75).toInt().coerceAtLeast(1)
                val tolerance = maxWords + (maxWords * 0.5).toInt() // 50% tolerance
                val chunks = TextChunker.chunk(text, maxTokens = maxTokens)
                for (chunk in chunks) {
                    val wc = chunk.text.split(Regex("\\s+")).count { it.isNotBlank() }
                    assertTrue(wc <= tolerance,
                        "Chunk has $wc words, exceeds tolerance $tolerance (maxTokens=$maxTokens)")
                }
            }
        }
    }

    @Test
    fun `empty and blank text returns empty list`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25),
                Arb.element("", " ", "\n", "\t", "  \n  \t  ")
            ) { blank ->
                val chunks = TextChunker.chunk(blank)
                assertTrue(chunks.isEmpty(), "Blank text must produce empty list")
            }
        }
    }
}
