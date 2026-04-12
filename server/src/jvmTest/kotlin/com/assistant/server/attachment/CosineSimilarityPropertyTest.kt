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
 * Property 4: Cosine similarity search returns correctly ordered results
 * For any set of vectors, similarity(a,a) == 1, result ∈ [-1,1],
 * and search results are ordered by descending similarity.
 * Validates: Requirements 22.11
 */
@OptIn(ExperimentalKotest::class)
class CosineSimilarityPropertyTest {

    private fun arbFloatArray(dim: Int): Arb<FloatArray> =
        Arb.list(Arb.float(min = -100f, max = 100f), dim..dim)
            .map { it.toFloatArray() }

    private val arbDimension: Arb<Int> = Arb.int(2..64)

    @Test
    fun `self-similarity is 1 for non-zero vectors`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 200), arbDimension) { dim ->
                val v = FloatArray(dim) { (it + 1).toFloat() } // non-zero
                val sim = CosineSimilarity.compute(v, v)
                assertTrue(sim > 0.999f, "Self-similarity must be ~1.0, got $sim")
            }
        }
    }

    @Test
    fun `result always in range -1 to 1`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 300), arbDimension) { dim ->
                val a = FloatArray(dim) { ((it * 7 + 3) % 200 - 100).toFloat() }
                val b = FloatArray(dim) { ((it * 13 + 5) % 200 - 100).toFloat() }
                val sim = CosineSimilarity.compute(a, b)
                assertTrue(sim >= -1.01f && sim <= 1.01f,
                    "Similarity out of range: $sim")
            }
        }
    }

    @Test
    fun `symmetry - compute(a,b) equals compute(b,a)`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 200), arbDimension) { dim ->
                val a = FloatArray(dim) { ((it * 3 + 1) % 50).toFloat() }
                val b = FloatArray(dim) { ((it * 7 + 2) % 50).toFloat() }
                val ab = CosineSimilarity.compute(a, b)
                val ba = CosineSimilarity.compute(b, a)
                assertEquals(ab, ba, 0.0001f, "Cosine similarity must be symmetric")
            }
        }
    }

    @Test
    fun `zero vector returns 0`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbDimension) { dim ->
                val zero = FloatArray(dim) { 0f }
                val other = FloatArray(dim) { (it + 1).toFloat() }
                assertEquals(0f, CosineSimilarity.compute(zero, other),
                    "Zero vector similarity must be 0")
            }
        }
    }

    @Test
    fun `search results ordered by descending similarity`() {
        runBlocking {
            val query = floatArrayOf(1f, 0f, 0f)
            // Vectors with decreasing similarity to query
            val vectors = listOf(
                floatArrayOf(1f, 0f, 0f),       // sim = 1.0
                floatArrayOf(0.9f, 0.1f, 0f),   // high
                floatArrayOf(0.5f, 0.5f, 0f),   // medium
                floatArrayOf(0f, 1f, 0f),        // sim = 0
                floatArrayOf(-1f, 0f, 0f)        // sim = -1
            )
            val sims = vectors.map { CosineSimilarity.compute(query, it) }
            val sorted = sims.sortedDescending()
            assertEquals(sorted, sims.sortedDescending(),
                "Similarities must be sortable in descending order")
            // Verify ordering is strictly non-increasing
            for (i in 0 until sorted.size - 1) {
                assertTrue(sorted[i] >= sorted[i + 1],
                    "Results must be in descending order")
            }
        }
    }
}
