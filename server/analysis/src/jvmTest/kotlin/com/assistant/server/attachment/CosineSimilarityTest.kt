package com.assistant.server.attachment

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CosineSimilarityTest {

    @Test
    fun `identical vectors return 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, CosineSimilarity.compute(v, v), 0.001f)
    }

    @Test
    fun `orthogonal vectors return 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, CosineSimilarity.compute(a, b), 0.001f)
    }

    @Test
    fun `zero vector returns 0`() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(1f, 2f)
        assertEquals(0f, CosineSimilarity.compute(a, b), 0.001f)
    }

    @Test
    fun `opposite vectors return -1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1f, CosineSimilarity.compute(a, b), 0.001f)
    }

    @Test
    fun `similar vectors return high similarity`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1.1f, 2.1f, 3.1f)
        val sim = CosineSimilarity.compute(a, b)
        assertTrue(sim > 0.99f, "Similar vectors should have high similarity: $sim")
    }

    @Test
    fun `different dimension vectors throw exception`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertThrows<IllegalArgumentException> {
            CosineSimilarity.compute(a, b)
        }
    }

    @Test
    fun `result is in range -1 to 1`() {
        val a = floatArrayOf(3f, -1f, 2f)
        val b = floatArrayOf(-2f, 4f, 1f)
        val sim = CosineSimilarity.compute(a, b)
        assertTrue(sim >= -1f && sim <= 1f, "Similarity out of range: $sim")
    }
}
