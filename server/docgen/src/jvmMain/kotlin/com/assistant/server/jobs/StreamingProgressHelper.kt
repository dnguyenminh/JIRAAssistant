package com.assistant.server.jobs

private const val STREAMING_PROGRESS_START = 35
private const val STREAMING_PROGRESS_END = 85
private const val THROTTLE_MIN_PROGRESS_DELTA = 1
private const val THROTTLE_MIN_TIME_MS = 2000L

/**
 * Maps streaming progress (0–100) to job progress (35–85).
 * Formula: jobProgress = 35 + (streamingProgress * 50) / 100
 *
 * Requirements: 2.2, 3.4
 */
internal fun mapStreamingToJobProgress(streamingProgress: Int): Int {
    return STREAMING_PROGRESS_START + (streamingProgress * 50) / 100
}

/**
 * Determines if a progress update should be written to DB based on throttle rules.
 * Write only when: progress changed ≥1% AND ≥2000ms elapsed since last write.
 * Final write at 85% (streaming done) always bypasses throttle.
 *
 * Requirements: 2.3, 5.1, 5.2, 5.3
 */
internal fun shouldWriteProgress(
    newProgress: Int,
    lastWrittenProgress: Int,
    nowMs: Long,
    lastWriteTimeMs: Long
): Boolean {
    if (newProgress == STREAMING_PROGRESS_END) return true
    val progressDelta = newProgress - lastWrittenProgress
    val timeDelta = nowMs - lastWriteTimeMs
    return progressDelta >= THROTTLE_MIN_PROGRESS_DELTA && timeDelta >= THROTTLE_MIN_TIME_MS
}
