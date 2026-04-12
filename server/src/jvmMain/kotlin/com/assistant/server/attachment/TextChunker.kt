package com.assistant.server.attachment

import com.assistant.server.attachment.models.TextChunk

/**
 * Splits text into chunks respecting paragraph/sentence boundaries.
 * Estimates 1 token ≈ 0.75 words → maxWords = maxTokens * 0.75.
 * Requirements: 22.10
 */
object TextChunker {

    private const val WORDS_PER_TOKEN = 0.75
    private val PARAGRAPH_SPLIT = Regex("\\n\\s*\\n")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+")

    /** Split text into chunks, each ≤ maxTokens estimated tokens. */
    fun chunk(text: String, maxTokens: Int = 1000): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        val maxWords = (maxTokens * WORDS_PER_TOKEN).toInt().coerceAtLeast(1)
        val paragraphs = text.split(PARAGRAPH_SPLIT).filter { it.isNotBlank() }
        val rawChunks = buildChunks(paragraphs, maxWords)
        return rawChunks.mapIndexed { i, t ->
            TextChunk(index = i, text = t, tokenCount = estimateTokens(t))
        }
    }

    internal fun buildChunks(paragraphs: List<String>, maxWords: Int): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        var bufferWordCount = 0
        for (para in paragraphs) {
            val segments = splitIfTooLong(para, maxWords)
            for (seg in segments) {
                val segWords = wordCount(seg)
                if (bufferWordCount + segWords > maxWords && bufferWordCount > 0) {
                    result.add(buffer.toString().trim())
                    buffer.clear(); bufferWordCount = 0
                }
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append(seg)
                bufferWordCount += segWords
            }
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString().trim())
        return result
    }

    /** Split a paragraph into sentences/words if it exceeds maxWords. */
    internal fun splitIfTooLong(text: String, maxWords: Int): List<String> {
        if (wordCount(text) <= maxWords) return listOf(text)
        val sentences = text.split(SENTENCE_SPLIT).filter { it.isNotBlank() }
        if (sentences.size > 1) return splitSentences(sentences, maxWords)
        return splitByWords(text, maxWords)
    }

    private fun splitSentences(sentences: List<String>, maxWords: Int): List<String> {
        val result = mutableListOf<String>()
        val buf = StringBuilder()
        var count = 0
        for (s in sentences) {
            val wc = wordCount(s)
            if (count + wc > maxWords && count > 0) {
                result.add(buf.toString().trim())
                buf.clear(); count = 0
            }
            if (buf.isNotEmpty()) buf.append(" ")
            buf.append(s)
            count += wc
        }
        if (buf.isNotEmpty()) result.add(buf.toString().trim())
        return result.flatMap { if (wordCount(it) > maxWords) splitByWords(it, maxWords) else listOf(it) }
    }

    private fun splitByWords(text: String, maxWords: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.chunked(maxWords) { it.joinToString(" ") }
    }

    internal fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotBlank() }

    internal fun estimateTokens(text: String): Int =
        (wordCount(text) / WORDS_PER_TOKEN).toInt().coerceAtLeast(1)
}
