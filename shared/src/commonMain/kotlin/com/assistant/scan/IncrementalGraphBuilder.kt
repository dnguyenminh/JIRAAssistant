package com.assistant.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages debounced, async incremental graph builds during scan.
 *
 * After each batch completes, [triggerBuild] resets a debounce timer.
 * When the timer fires, delegates to [BatchScanEngine.buildAndSaveGraph].
 * Overlapping builds are skipped via [building] flag.
 *
 * Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 4.3
 */
class IncrementalGraphBuilder(
    private val engine: BatchScanEngine,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 5000L
) {
    private var pendingJob: Job? = null
    private val mutex = Mutex()
    private var building = false

    /**
     * Trigger an incremental graph build with debounce.
     * Returns immediately — actual build runs in a separate coroutine.
     * Resets debounce timer on each call; skips if already building.
     */
    fun triggerBuild(projectKey: String) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMs)
            executeBuild(projectKey)
        }
    }

    /** Cancel any pending debounce job (called on scan cancel/pause). */
    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
    }

    private suspend fun executeBuild(projectKey: String) {
        val acquired = mutex.withLock {
            if (building) return@withLock false
            building = true
            true
        }
        if (!acquired) return
        try {
            engine.buildAndSaveGraph(projectKey)
        } catch (e: Exception) {
            engine.logToBoth(
                projectKey, "-", ScanLogStatus.FAILED,
                "Incremental graph build failed: ${e.message ?: "Unknown error"}"
            )
        } finally {
            mutex.withLock { building = false }
        }
    }
}
