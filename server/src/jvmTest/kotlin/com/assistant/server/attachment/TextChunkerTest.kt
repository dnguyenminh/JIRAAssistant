package com.assistant.server.attachment

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    @Test
    fun `empty text returns empty list`() {
        assertEquals(emptyList(), TextChunker.chunk(""))
    }

    @Test
    fun `blank text returns empty list`() {
        assertEquals(emptyList(), TextChunker.chunk("   \n  "))
    }

    @Test
    fun `short text returns single chunk`() {
        val chunks = TextChunker.chunk("Hello world", maxTokens = 1000)
        assertEquals(1, chunks.size)
        assertEquals("Hello world", chunks[0].text)
        assertEquals(0, chunks[0].index)
    }

    @Test
    fun `long text splits into multiple chunks`() {
        val longText = (1..1000).joinToString(" ") { "word$it" }
        val chunks = TextChunker.chunk(longText, maxTokens = 100)
        assertTrue(chunks.size > 1, "Should split into multiple chunks")
        for (chunk in chunks) {
            assertTrue(chunk.tokenCount <= 150, "Chunk too large: ${chunk.tokenCount}")
        }
    }

    @Test
    fun `chunk indices are sequential starting from 0`() {
        val text = (1..500).joinToString(" ") { "word$it" }
        val chunks = TextChunker.chunk(text, maxTokens = 50)
        chunks.forEachIndexed { i, chunk ->
            assertEquals(i, chunk.index, "Chunk index mismatch at position $i")
        }
    }

    @Test
    fun `paragraph boundaries are respected`() {
        val text = "First paragraph here.\n\nSecond paragraph here."
        val chunks = TextChunker.chunk(text, maxTokens = 1000)
        assertEquals(1, chunks.size, "Short paragraphs should merge into one chunk")
        assertTrue(chunks[0].text.contains("First paragraph"))
        assertTrue(chunks[0].text.contains("Second paragraph"))
    }

    @Test
    fun `each chunk has positive token count`() {
        val text = "Some text with multiple words for testing"
        val chunks = TextChunker.chunk(text, maxTokens = 100)
        for (chunk in chunks) {
            assertTrue(chunk.tokenCount > 0, "Token count should be positive")
        }
    }
}
