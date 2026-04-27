package com.assistant.server.jobs

import com.assistant.ai.*
import com.assistant.document.DocumentAggregator
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.JobRepository
import kotlinx.coroutines.runBlocking

private const val MAX_RETRIES = 2

/**
 * Testable JobExecutor that exposes streaming integration
 * logic and captures progress writes + log messages.
 */
class TestableJobExecutor(
    private val jobRepository: JobRepository,
    documentRepository: DocumentRepository,
    private val agent: AIAgent
) : JobExecutor(
    aggregator = object : DocumentAggregator {
        override suspend fun aggregate(ticketId: String) =
            error("Not used in streaming tests")
    },
    documentRepository = documentRepository,
    jobRepository = jobRepository
) {
    data class ProgressWrite(val jobId: String, val progress: Int)

    val progressWrites = mutableListOf<ProgressWrite>()
    val loggedWarnings = mutableListOf<String>()

    /**
     * Mirrors callAIWithRetry logic for testability.
     * Retries up to MAX_RETRIES, resets progress to 35% each attempt.
     */
    suspend fun runCallAI(jobId: String, agent: AIAgent, prompt: String): String {
        writeProgress(jobId, 35)
        var lastError: String? = null
        for (attempt in 0..MAX_RETRIES) {
            writeProgress(jobId, 35)
            val result = callOnce(jobId, agent, prompt)
            when (result) {
                is AIResult.Success -> return result.response
                is AIResult.Failure -> lastError = result.error
            }
        }
        error("AI failed after ${MAX_RETRIES + 1} attempts: $lastError")
    }

    private suspend fun callOnce(
        jobId: String, agent: AIAgent, prompt: String
    ): AIResult {
        if (agent is FakeOllamaAgent) {
            return tryStreamingFallback(jobId, agent, prompt)
        }
        return agent.analyze(prompt)
    }

    private suspend fun tryStreamingFallback(
        jobId: String, agent: FakeOllamaAgent, prompt: String
    ): AIResult {
        return try {
            callStreaming(jobId, agent, prompt)
        } catch (e: Exception) {
            val msg = "Streaming failed, falling back to non-streaming: ${e.message}"
            loggedWarnings.add(msg)
            agent.analyze(prompt)
        }
    }

    private suspend fun callStreaming(
        jobId: String, agent: FakeOllamaAgent, prompt: String
    ): AIResult {
        var lastWrittenProgress = 35
        var lastWriteTime = System.currentTimeMillis()
        val callback: (Int) -> Unit = { streamingProgress ->
            val jobProgress = mapStreamingToJobProgress(streamingProgress)
            val now = System.currentTimeMillis()
            if (shouldWriteProgress(jobProgress, lastWrittenProgress, now, lastWriteTime)) {
                lastWrittenProgress = jobProgress
                lastWriteTime = now
                runBlocking { writeProgress(jobId, jobProgress) }
            }
        }
        val result = agent.analyzeStreaming(prompt, callback)
        writeProgress(jobId, 85)
        return result
    }

    private suspend fun writeProgress(jobId: String, progress: Int) {
        progressWrites.add(ProgressWrite(jobId, progress))
        jobRepository.updateStatus(jobId, "RUNNING", progress, "GENERATING_DOCUMENT")
    }
}
