package com.assistant.domain

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property-based test for Property 5: Scrum Point Scale Invariant.
 *
 * **Validates: Requirements 5.6**
 *
 * For any Double value (including NaN, Infinity, -Infinity, negatives, very large/small numbers),
 * findClosestAllowedPoint() SHALL return a value in the allowed set {0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40}.
 *
 * Feature: jira-assistant-app, Property 5: Scrum Point Scale Invariant
 */
class ScrumEstimatorPropertyTest {

    private val allowedPoints = setOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)

    private val noopAgent = object : AIAgent {
        override suspend fun analyze(prompt: String, context: AIContext?): AIResult =
            AIResult.Failure("noop")
        override fun getAgentName(): String = "noop"
    }

    /** Generator that produces arbitrary doubles including edge cases. */
    private fun arbDoubleWithEdgeCases(): Arb<Double> = arbitrary(
        edgecases = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            0.0,
            -0.0,
            Double.MAX_VALUE,
            -Double.MAX_VALUE,
            Double.MIN_VALUE,
            -1.0,
            -1000.0,
            0.5,
            40.0,
            100.0,
            -999999.0
        )
    ) {
        Arb.double().bind()
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 5 - findClosestAllowedPoint always returns a value in the allowed Scrum point set`() = runTest {
        val estimator = ScrumEstimator(noopAgent)

        checkAll(PropTestConfig(iterations = 25), arbDoubleWithEdgeCases()) { input ->
            val result = estimator.findClosestAllowedPoint(input)
            assertTrue(
                result in allowedPoints,
                "findClosestAllowedPoint($input) returned $result which is not in the allowed set $allowedPoints"
            )
        }
    }
}
