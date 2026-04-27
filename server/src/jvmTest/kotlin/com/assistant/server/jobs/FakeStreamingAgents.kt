package com.assistant.server.jobs

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult

/**
 * Fake OllamaAgent for unit tests — simulates streaming behavior
 * without real HTTP calls. Extends AIAgent but adds analyzeStreaming.
 */
class FakeOllamaAgent(
    private val streamResult: AIResult? = null,
    private val streamResults: List<AIResult>? = null,
    private val throwOnStream: Boolean = false,
    private val fallbackResult: AIResult = AIResult.Success("fallback")
) : AIAgent {

    var streamingCalled = false
    var analyzeCalled = false
    private var callIndex = 0

    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        analyzeCalled = true
        return fallbackResult
    }

    suspend fun analyzeStreaming(
        prompt: String,
        onProgress: (Int) -> Unit,
        context: AIContext? = null
    ): AIResult {
        streamingCalled = true
        if (throwOnStream) throw RuntimeException("Stream connection failed")
        onProgress(50)
        onProgress(100)
        val result = streamResults?.getOrNull(callIndex++) ?: streamResult
        return result ?: AIResult.Failure("No result configured")
    }

    override fun getAgentName(): String = "FakeOllama"
}

/**
 * Fake non-streaming agent (e.g., GeminiAgent) for testing
 * the non-OllamaAgent code path.
 */
class FakeNonStreamingAgent(
    private val result: AIResult
) : AIAgent {

    var analyzeCalled = false

    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        analyzeCalled = true
        return result
    }

    override fun getAgentName(): String = "FakeGemini"
}
