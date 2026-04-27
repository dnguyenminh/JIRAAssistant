package com.assistant.ai

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tests connectivity to AI providers.
 * Extracted from AIOrchestratorImpl for SRP compliance.
 */
internal object ProviderTester {

    suspend fun test(agent: AIAgent, providerId: String): ProviderTestResult {
        val start = currentTimeMillis()
        return try {
            if (agent is OllamaAgent) testOllama(agent, providerId, start)
            else testGeneric(agent, providerId, start)
        } catch (e: Exception) {
            val latency = currentTimeMillis() - start
            ProviderTestResult(providerId, false, latency, e.message ?: "Connection failed")
        }
    }

    private suspend fun testOllama(
        agent: OllamaAgent, providerId: String, start: Long
    ): ProviderTestResult {
        val result = agent.testConnection()
        val latency = currentTimeMillis() - start
        return if (result != null) ProviderTestResult(providerId, true, latency, result)
        else ProviderTestResult(providerId, false, latency, "Connection failed")
    }

    private suspend fun testGeneric(
        agent: AIAgent, providerId: String, start: Long
    ): ProviderTestResult {
        val result = withTimeoutOrNull(5000L) { agent.analyze("ping") }
        val latency = currentTimeMillis() - start
        return when (result) {
            is AIResult.Success -> ProviderTestResult(providerId, true, latency, "Connected")
            is AIResult.Failure -> ProviderTestResult(providerId, false, latency, result.error)
            null -> ProviderTestResult(providerId, false, latency, "Timeout")
        }
    }
}
