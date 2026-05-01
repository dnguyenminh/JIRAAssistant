package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchService

/**
 * Watches the agent home directory for file changes and triggers
 * hot-reload via [AgentHomeDirectory.reload].
 *
 * Monitors the `.agent/skills/`, `.agent/rules/`, and `.agent/workflows/`
 * subdirectories for file creation, modification, and deletion events.
 * Rapid changes are debounced with a configurable delay (default 500ms)
 * to avoid redundant reloads.
 *
 * Usage:
 * ```
 * val watcher = AgentHomeDirectoryWatcher(loader, basePath)
 * watcher.start(scope)
 * // ... later ...
 * watcher.stop()
 * ```
 *
 * @param loader the [AgentHomeDirectory] whose [AgentHomeDirectory.reload]
 *   method is called when file changes are detected
 * @param basePath the root path of the agent home directory
 * @param debounceMs delay in milliseconds before triggering reload
 *   after the last detected change (default: 500)
 */
class AgentHomeDirectoryWatcher(
    private val loader: AgentHomeDirectory,
    private val basePath: Path,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS
) {

    private val logger = LoggerFactory.getLogger(AgentHomeDirectoryWatcher::class.java)

    private var watchJob: Job? = null
    private var watchService: WatchService? = null

    /**
     * Starts watching the agent home subdirectories for file changes.
     *
     * Launches a coroutine on [Dispatchers.IO] that polls the
     * [WatchService] and calls [AgentHomeDirectory.reload] when
     * changes are detected (with debounce).
     *
     * @param scope the [CoroutineScope] used to launch the watcher coroutine
     */
    fun start(scope: CoroutineScope) {
        if (watchJob?.isActive == true) {
            logger.warn("Watcher already running for {}", basePath)
            return
        }

        val service = createWatchService() ?: return
        watchService = service

        registerDirectories(service)

        watchJob = scope.launch(Dispatchers.IO) {
            logger.info("Started watching agent home directory: {}", basePath)
            pollLoop(service)
            logger.info("Stopped watching agent home directory: {}", basePath)
        }
    }

    /**
     * Stops the file watcher and releases resources.
     *
     * Cancels the watching coroutine and closes the [WatchService].
     */
    fun stop() {
        watchJob?.cancel()
        watchJob = null
        closeWatchService()
        logger.info("Watcher stopped for {}", basePath)
    }

    private fun createWatchService(): WatchService? {
        return try {
            FileSystems.getDefault().newWatchService()
        } catch (e: Exception) {
            logger.error("Failed to create WatchService for {}: {}", basePath, e.message)
            null
        }
    }

    private fun registerDirectories(service: WatchService) {
        for (dirName in WATCHED_DIRECTORIES) {
            registerDirectory(service, basePath.resolve(dirName))
        }
    }

    private fun registerDirectory(service: WatchService, dir: Path) {
        if (!Files.isDirectory(dir)) {
            logger.debug("Directory does not exist, skipping watch: {}", dir)
            return
        }
        try {
            dir.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
            logger.debug("Registered watch on directory: {}", dir)
        } catch (e: Exception) {
            logger.warn("Failed to register watch on {}: {}", dir, e.message)
        }
    }

    private suspend fun pollLoop(service: WatchService) {
        var pendingReload = false
        var lastEventTime = 0L

        while (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive != false) {
            val hasEvents = pollEvents(service)
            if (hasEvents) {
                pendingReload = true
                lastEventTime = System.currentTimeMillis()
            }

            if (pendingReload && isDebounceElapsed(lastEventTime)) {
                pendingReload = false
                triggerReload()
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun pollEvents(service: WatchService): Boolean {
        val key = service.poll() ?: return false
        val events = key.pollEvents()
        val hasEvents = events.isNotEmpty()

        if (hasEvents) {
            logDetectedEvents(events.size)
        }

        key.reset()
        return hasEvents
    }

    private fun logDetectedEvents(count: Int) {
        logger.debug("Detected {} file change(s) in agent home directory", count)
    }

    private fun isDebounceElapsed(lastEventTime: Long): Boolean {
        return System.currentTimeMillis() - lastEventTime >= debounceMs
    }

    private fun triggerReload() {
        try {
            loader.reload()
            logger.info("Reloaded agent home directory after file changes")
        } catch (e: Exception) {
            logger.error("Failed to reload agent home directory: {}", e.message)
        }
    }

    private fun closeWatchService() {
        try {
            watchService?.close()
            watchService = null
        } catch (e: Exception) {
            logger.warn("Error closing WatchService: {}", e.message)
        }
    }

    companion object {
        private const val DEFAULT_DEBOUNCE_MS = 500L
        private const val POLL_INTERVAL_MS = 200L

        private val WATCHED_DIRECTORIES = listOf(
            ".agent/skills",
            ".agent/rules",
            ".agent/workflows"
        )
    }
}
