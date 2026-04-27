package com.assistant.ai.deepanalysis

import kotlin.math.abs

/**
 * Validates and normalizes Scrum Points to the valid Fibonacci-like scale.
 * Valid values: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40
 *
 * Requirements: 25.4
 */
internal object ScrumPointsValidator {

    private val VALID_POINTS = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)

    /**
     * Round the given value to the nearest valid Scrum Point.
     * If the value is already valid, returns it unchanged.
     * Negative values are clamped to 0.
     */
    fun normalize(value: Double): Double {
        if (value < 0.0) return 0.0
        return VALID_POINTS.minByOrNull { abs(it - value) } ?: 0.0
    }

    /**
     * Check if a value is in the valid Scrum Points scale.
     */
    fun isValid(value: Double): Boolean {
        return VALID_POINTS.any { abs(it - value) < 0.001 }
    }
}
