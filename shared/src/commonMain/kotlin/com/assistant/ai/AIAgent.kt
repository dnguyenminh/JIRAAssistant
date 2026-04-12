package com.assistant.ai

import kotlinx.serialization.Serializable

/**
 * Common interface for all AI Agents (Local and Cloud).
 * Strictly following Interface Segregation and Dependency Inversion.
 */
interface AIAgent {
    /**
     * Sends a prompt to the AI agent and returns the response.
     */
    suspend fun analyze(prompt: String, context: AIContext? = null): AIResult

    /**
     * Returns the name of the agent (e.g., "Ollama - Llama 3", "Gemini 1.5 Pro").
     */
    fun getAgentName(): String
}

/**
 * Context for AI analysis, containing optional ticket history or feature data.
 */
@Serializable
data class AIContext(
    val tickets: List<JiraTicketSummary> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class JiraTicketSummary(
    val id: String,
    val summary: String,
    val description: String,
    val status: String,
    val resolution: String? = null
)

/**
 * Sealed class for AI results to handle success and failure states.
 */
@Serializable
sealed class AIResult {
    @Serializable
    data class Success(val response: String, val tokens: Int? = null) : AIResult()
    
    @Serializable
    data class Failure(val error: String) : AIResult()
}
