package com.assistant.server.agent.streaming

import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.streaming.StreamingCallback
import com.assistant.agent.streaming.StreamingConfig
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bridges subprocess stdout output to registered [StreamingCallback]
 * instances.
 *
 * Consumes a [Flow] of text chunks (typically from subprocess stdout)
 * and forwards each chunk to all registered callbacks. Integrates with
 * [ProgressReporter] for both phase-level and chunk-level progress.
 *
 * When a callback throws (e.g., interrupted client connection), the
 * adapter catches the exception, logs a warning, unregisters the
 * failing callback, and continues — ensuring agent execution is never
 * interrupted by client disconnects (Requirement 17.5).
 *
 * Requirements: 17.1, 17.2, 17.3, 17.4, 17.5
 *
 * @property reporter progress reporter for chunk-level updates
 * @property config streaming configuration (enabled flag, buffer size)
 */
class StreamingOutputAdapter(
    private val reporter: ProgressReporter,
    private val config: StreamingConfig = StreamingConfig()
) {

    private val logger = LoggerFactory.getLogger(
        StreamingOutputAdapter::class.java
    )
    private val callbacks = CopyOnWriteArrayList<StreamingCallback>()
    private val buffer = StringBuilder()
    private var chunksReceived = 0

    /** Registers a callback to receive streaming output chunks. */
    fun registerCallback(callback: StreamingCallback) {
        callbacks.add(callback)
        logger.debug("Callback registered, total={}", callbacks.size)
    }

    /** Removes a previously registered callback. */
    fun unregisterCallback(callback: StreamingCallback) {
        callbacks.remove(callback)
        logger.debug("Callback unregistered, total={}", callbacks.size)
    }

    /**
     * Consumes a [Flow] of text chunks and forwards them to all
     * registered callbacks. Reports progress to [reporter].
     *
     * If streaming is disabled via [config], the flow is still
     * collected but chunks are not forwarded to callbacks.
     */
    suspend fun processStream(stream: Flow<String>) {
        chunksReceived = 0
        buffer.clear()
        stream.collect { chunk -> handleChunk(chunk) }
        flushBuffer()
    }

    /** Returns the number of currently registered callbacks. */
    fun callbackCount(): Int = callbacks.size

    private suspend fun handleChunk(chunk: String) {
        chunksReceived++
        reportChunkProgress()
        if (!config.enabled) return
        bufferAndForward(chunk)
    }

    private suspend fun reportChunkProgress() {
        val percent = chunksReceived.coerceAtMost(100)
        reporter.reportProgress(
            percent,
            "Streaming chunk $chunksReceived"
        )
    }

    private suspend fun bufferAndForward(chunk: String) {
        buffer.append(chunk)
        if (buffer.length >= config.bufferSize) {
            flushBuffer()
        }
    }

    private suspend fun flushBuffer() {
        if (buffer.isEmpty()) return
        val content = buffer.toString()
        buffer.clear()
        forwardToCallbacks(content)
    }

    private suspend fun forwardToCallbacks(content: String) {
        val progress = chunksReceived.coerceAtMost(100)
        for (callback in callbacks) {
            deliverSafely(callback, content, progress)
        }
    }

    private fun deliverSafely(
        callback: StreamingCallback,
        content: String,
        progress: Int
    ) {
        try {
            callback.onUpdate(content, progress)
        } catch (e: Exception) {
            handleCallbackFailure(callback, e)
        }
    }

    private fun handleCallbackFailure(
        callback: StreamingCallback,
        error: Exception
    ) {
        logger.warn(
            "Callback failed, unregistering: {}",
            error.message
        )
        callbacks.remove(callback)
    }
}
