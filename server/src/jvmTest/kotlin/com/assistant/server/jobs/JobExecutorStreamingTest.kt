package com.assistant.server.jobs

import com.assistant.ai.AIAgent
import com.assistant.ai.AIResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for JobExecutor streaming integration.
 *
 * Validates: Requirements 2.1, 2.4, 2.5, 2.6, 4.4, 5.2
 */
class JobExecutorStreamingTest {

    private val jobRepo = InMemoryJobRepository()
    private val docRepo = InMemoryDocumentRepository()

    private fun createExecutor(
        agent: AIAgent
    ): TestableJobExecutor {
        return TestableJobExecutor(
            jobRepository = jobRepo,
            documentRepository = docRepo,
            agent = agent
        )
    }

    /** Req 2.1 — OllamaAgent dispatches to analyzeStreaming(). */
    @Test
    fun `OllamaAgent dispatches to analyzeStreaming`() = runTest {
        val agent = FakeOllamaAgent(streamResult = AIResult.Success("doc"))
        val executor = createExecutor(agent)
        executor.runCallAI("job-1", agent, "prompt")
        assertTrue(agent.streamingCalled, "analyzeStreaming should be called")
        assertFalse(agent.analyzeCalled, "analyze should NOT be called")
    }

    /** Req 2.5 — non-OllamaAgent uses analyze(). */
    @Test
    fun `non-OllamaAgent uses analyze`() = runTest {
        val agent = FakeNonStreamingAgent(AIResult.Success("doc"))
        val executor = createExecutor(agent)
        executor.runCallAI("job-2", agent, "prompt")
        assertTrue(agent.analyzeCalled, "analyze should be called")
    }

    /** Req 2.4 — streaming completion sets progress to 85%. */
    @Test
    fun `streaming completion sets progress to 85 percent`() = runTest {
        val agent = FakeOllamaAgent(streamResult = AIResult.Success("doc"))
        val executor = createExecutor(agent)
        seedJob("job-3")
        executor.runCallAI("job-3", agent, "prompt")
        val job = jobRepo.findById("job-3")!!
        assertEquals(85, job.progressPercent)
    }

    /** Req 2.6 — retry resets progress to 35%. */
    @Test
    fun `retry resets progress to 35 percent`() = runTest {
        val agent = FakeOllamaAgent(
            streamResults = listOf(AIResult.Failure("err"), AIResult.Success("ok"))
        )
        val executor = createExecutor(agent)
        seedJob("job-4")
        executor.runCallAI("job-4", agent, "prompt")
        val writes = executor.progressWrites
        val resetWrites = writes.filter { it.progress == 35 }
        assertTrue(
            resetWrites.size >= 2,
            "Expected at least 2 writes at 35% (initial + retry), got ${resetWrites.size}"
        )
    }

    /** Req 4.4 — fallback logs warning message. */
    @Test
    fun `fallback logs warning on streaming exception`() = runTest {
        val agent = FakeOllamaAgent(throwOnStream = true)
        val executor = createExecutor(agent)
        seedJob("job-5")
        executor.runCallAI("job-5", agent, "prompt")
        assertTrue(
            executor.loggedWarnings.any { it.contains("Streaming failed") },
            "Expected warning about streaming fallback"
        )
    }

    /** Req 5.2 — final 85% write bypasses throttle. */
    @Test
    fun `final 85 percent write bypasses throttle`() = runTest {
        val agent = FakeOllamaAgent(streamResult = AIResult.Success("doc"))
        val executor = createExecutor(agent)
        seedJob("job-6")
        executor.runCallAI("job-6", agent, "prompt")
        val writes = executor.progressWrites
        assertTrue(
            writes.any { it.progress == 85 },
            "85% write must always occur after streaming completes"
        )
    }

    private suspend fun seedJob(jobId: String) {
        jobRepo.create(
            com.assistant.document.models.GenerationJob(
                jobId = jobId, ticketId = "T-1",
                documentType = "BRD", status = "RUNNING",
                createdBy = "test", createdAt = "now", updatedAt = "now"
            )
        )
    }
}
