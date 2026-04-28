package com.assistant.server.attachment

import kotlin.math.sqrt

/**
 * Cosine similarity between two vectors.
 * Returns value in [-1, 1]. Zero vectors return 0.
 * Requirements: 22.11
 */
object CosineSimilarity {

    fun compute(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}
