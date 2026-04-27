package com.assistant.server.jobs

import com.assistant.ai.AIResult
import com.assistant.ai.OllamaAgent
import com.assistant.ai.models.OllamaStreamLine
import com.assistant.document.models.GenerationJob
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test for the full streaming pipeline:
 * OllamaAgent.analyzeStreaming() → mock Ollama HTTP server (NDJSON)
 * → progress callback with throttle → JobRepository writes.
 *
 * Validates: Requirements 1.1, 2.2, 2.3, 5.1
 */
class StreamingPipelineIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var jobRepo: InMemoryJobRepository
    private val jobId = "integration-job-1"

    @BeforeEach
    fun setUp() {
        jobRepo = InMemoryJobRepository()
        runBlocking { seedJob(jobId) }
    }

    /**
     * End-to-end: MockEngine NDJSON → OllamaAgent.analyzeStreaming()
     * → throttled progress writes → JobRepository progress 35→85.
     */
    @Test
    fun `full pipeline streams NDJSON and updates progress 35 to 85`() =
        runTest {
            val chunks = buildChunks(count = 20, text = "word ")
            val client = createNdjsonMockClient(chunks)
            val agent = OllamaAgent(client, "llama3", "http://mock")
            val writes = mutableListOf<Int>()

            val result = runStreamingPipeline(agent, writes)

            assertTrue(result is AIResult.Success, "Expected Success")
            assertEquals("word ".repeat(20), (result as AIResult.Success).response)
            assertProgressRange(writes)
            assertFinalWriteIs85(writes)
        }

    /**
     * Verifies accumulated text from all NDJSON partial responses
     * matches the expected concatenation.
     */
    @Test
    fun `accumulated text matches all NDJSON response fields`() =
        runTest {
            val texts = listOf("# BRD\n", "## Section 1\n", "Content here.")
            val client = createNdjsonMockClient(texts)
            val agent = OllamaAgent(client, "llama3", "http://mock")
            val writes = mutableListOf<Int>()

            val result = runStreamingPipeline(agent, writes)

            assertTrue(result is AIResult.Success, "Expected Success")
            val text = (result as AIResult.Success).response
            assertEquals("# BRD\n## Section 1\nContent here.", text)
        }

    /**
     * Verifies throttle: not every NDJSON line triggers a DB write.
     * With 200 lines, far fewer DB writes should occur due to
     * ≥1% progress delta + ≥2000ms time gate.
     */
    @Test
    fun `throttle reduces DB writes below NDJSON line count`() =
        runTest {
            val lineCount = 200
            val chunks = buildChunks(count = lineCount, text = "t ")
            val client = createNdjsonMockClient(chunks)
            val agent = OllamaAgent(client, "llama3", "http://mock")
            val writes = mutableListOf<Int>()

            val result = runStreamingPipeline(agent, writes)

            assertTrue(result is AIResult.Success, "Expected Success")
            // writes include initial 35 + throttled intermediates + final 85
            // With fast execution, throttle time gate blocks most writes
            assertTrue(
                writes.size < lineCount,
                "Expected fewer DB writes (${writes.size}) than " +
                    "NDJSON lines ($lineCount) due to throttle"
            )
            assertFinalWriteIs85(writes)
        }

    // --- Pipeline orchestration (mirrors JobExecutor logic) ---

    private suspend fun runStreamingPipeline(
        agent: OllamaAgent,
        writes: MutableList<Int>
    ): AIResult {
        writeProgress(jobId, 35, writes)
        var lastWrittenProgress = 35
        var lastWriteTime = System.currentTimeMillis()

        val callback: (Int) -> Unit = { streamPct ->
            val jobPct = mapStreamingToJobProgress(streamPct)
            val now = System.currentTimeMillis()
            val shouldWrite = shouldWriteProgress(
                jobPct, lastWrittenProgress, now, lastWriteTime
            )
            if (shouldWrite) {
                lastWrittenProgress = jobPct
                lastWriteTime = now
                runBlocking { writeProgress(jobId, jobPct, writes) }
            }
        }

        val result = agent.analyzeStreaming("Generate BRD", callback)
        writeProgress(jobId, 85, writes)
        return result
    }

    private suspend fun writeProgress(
        jobId: String, progress: Int, writes: MutableList<Int>
    ) {
        writes.add(progress)
        jobRepo.updateStatus(
            jobId, "RUNNING", progress, "GENERATING_DOCUMENT"
        )
    }

    // --- Mock HTTP helpers ---

    private fun createNdjsonMockClient(
        texts: List<String>
    ): HttpClient {
        val body = buildNdjsonBody(texts)
        return HttpClient(MockEngine) {
            engine {
                addHandler { respond(body, HttpStatusCode.OK) }
            }
        }
    }

    private fun buildNdjsonBody(texts: List<String>): String {
        val sb = StringBuilder()
        for (text in texts) {
            val line = OllamaStreamLine(
                model = "llama3", response = text,
                done = false, createdAt = "2025-01-01T00:00:00Z"
            )
            sb.appendLine(json.encodeToString(line))
        }
        val done = OllamaStreamLine(
            model = "llama3", response = "", done = true,
            doneReason = "stop", createdAt = "2025-01-01T00:00:00Z"
        )
        sb.appendLine(json.encodeToString(done))
        return sb.toString()
    }

    private fun buildChunks(count: Int, text: String): List<String> =
        (1..count).map { text }

    // --- Assertions ---

    private fun assertProgressRange(writes: List<Int>) {
        assertTrue(writes.first() == 35, "First write should be 35%")
        assertTrue(writes.last() == 85, "Last write should be 85%")
        for (w in writes) {
            assertTrue(
                w in 35..85,
                "Progress write $w outside [35, 85]"
            )
        }
    }

    private fun assertFinalWriteIs85(writes: List<Int>) {
        assertEquals(85, writes.last(), "Final write must be 85%")
    }

    private suspend fun seedJob(jobId: String) {
        jobRepo.create(
            GenerationJob(
                jobId = jobId, ticketId = "T-INT-1",
                documentType = "BRD", status = "RUNNING",
                createdBy = "test", createdAt = "now", updatedAt = "now"
            )
        )
    }
}
