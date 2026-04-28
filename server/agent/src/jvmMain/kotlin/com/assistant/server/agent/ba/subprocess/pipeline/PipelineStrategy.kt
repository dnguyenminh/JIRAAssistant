package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskConfig
import com.assistant.agent.ba.models.BATaskResult
import com.assistant.agent.progress.ProgressReporter

/**
 * Strategy interface for BA document generation pipelines.
 *
 * Implementations define how a [BATaskConfig] is executed to produce
 * a [BATaskResult]. Follows the Strategy Pattern to allow switching
 * between [MultiTurnPipelineStrategy] and [LegacyToolCallStrategy].
 */
interface PipelineStrategy {
    suspend fun execute(
        config: BATaskConfig,
        progressReporter: ProgressReporter
    ): BATaskResult
}
