package com.assistant.agent.streaming

import kotlinx.serialization.Serializable

/**
 * Functional interface for receiving streaming output chunks from agent subprocesses.
 *
 * Specialized agents and the Orchestrator use this callback to emit intermediate
 * results during phase execution, enabling near-real-time UI updates. Each invocation
 * delivers a text chunk and a progress indicator so consumers can render progressive
 * output without waiting for the entire response to complete.
 *
 * The [StreamingOutputAdapter] (server module) bridges this callback with the
 * existing [com.assistant.agent.progress.ProgressReporter] for unified progress
 * tracking.
 *
 * @see StreamingConfig
 */
fun interface StreamingCallback {

    /**
     * Called when a new output chunk is available.
     *
     * @param chunk The text content of this streaming chunk
     * @param progress Progress indicator (0–100) representing overall completion
     */
    fun onUpdate(chunk: String, progress: Int)
}

/**
 * Configuration for streaming output behavior.
 *
 * Controls whether streaming is active and how chunks are batched before
 * delivery. Used by the [StreamingOutputAdapter] to tune throughput vs.
 * latency when forwarding subprocess stdout to registered callbacks.
 *
 * @property enabled Whether streaming output is active (default: true)
 * @property bufferSize Buffer size in bytes for batching chunks before delivery (default: 1024)
 */
@Serializable
data class StreamingConfig(
    val enabled: Boolean = true,
    val bufferSize: Int = 1024
)
