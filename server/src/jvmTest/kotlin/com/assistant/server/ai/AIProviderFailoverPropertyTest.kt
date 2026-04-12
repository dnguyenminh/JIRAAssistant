package com.assistant.server.ai

import com.assistant.ai.*
import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 13: AI Provider Failover Priority Selection
 *
 * For any list of ProviderConfig with different priorities and statuses,
 * AI_Orchestrator SHALL select the provider with the highest priority (lowest number)
 * that is ACTIVE. If the current provider goes OFFLINE, the orchestrator SHALL
 * automatically select the next ACTIVE provider by priority.
 *
 * **Validates: Requirements 12.2, 12.3**
 *
 * Feature: jira-assistant-app, Property 13: AI Provider Failover Priority Selection
 */
@OptIn(ExperimentalKotest::class)
class AIProviderFailoverPropertyTest {

    /** Empty KBRepository — always returns null (KB miss). */
    class EmptyKBRepository : KBRepository {
        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord): Boolean = true
        override suspend fun overwrite(record: KBRecord): Boolean = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }

    /** AI Agent that succeeds with a valid JSON response. */
    class SuccessAgent(private val name: String) : AIAgent {
        var callCount = 0; private set
        override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
            callCount++
            return AIResult.Success("""{"requirementSummary":{"unified":"from $name"},"evolution":[],"complexity":{"scrumPoints":5.0,"description":"by $name"}}""")
        }
        override fun getAgentName(): String = name
        fun reset() { callCount = 0 }
    }

    /** AI Agent that always fails. */
    class FailingAgent(private val name: String) : AIAgent {
        var callCount = 0; private set
        override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
            callCount++
            return AIResult.Failure("$name is down")
        }
        override fun getAgentName(): String = name
        fun reset() { callCount = 0 }
    }

    private val arbConnectionStatus: Arb<ConnectionStatus> = Arb.enum<ConnectionStatus>()

    private val arbProviderType: Arb<ProviderType> = Arb.element(
        ProviderType.OLLAMA, ProviderType.GEMINI, ProviderType.LM_STUDIO
    )

    /**
     * Generate a list of 2-5 provider configs with unique IDs and distinct priorities.
     */
    private val arbProviderConfigs: Arb<List<ProviderConfig>> = Arb.bind(
        Arb.int(2..5),
        Arb.list(arbConnectionStatus, 2..5)
    ) { count, statuses ->
        val names = listOf("ollama", "gemini", "lmstudio", "gemini-cli", "custom")
        (0 until count).map { i ->
            ProviderConfig(
                providerId = names[i],
                name = names[i].replaceFirstChar { it.uppercase() },
                type = when (i) { 0 -> ProviderType.OLLAMA; 1 -> ProviderType.GEMINI; else -> ProviderType.LM_STUDIO },
                endpoint = "http://localhost:${11434 + i}",
                priority = i,
                status = statuses.getOrElse(i) { ConnectionStatus.OFFLINE }
            )
        }
    }

    @Test
    fun `orchestrator selects highest priority ACTIVE provider`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbProviderConfigs) { configs ->
                val activeConfigs = configs.filter { it.status == ConnectionStatus.ACTIVE }
                    .sortedBy { it.priority }

                // Create agents: each SuccessAgent embeds its name in the response
                val agents = mutableMapOf<String, AIAgent>()
                val successAgents = mutableMapOf<String, SuccessAgent>()
                configs.forEach { config ->
                    val agent = SuccessAgent(config.providerId)
                    agents[config.providerId] = agent
                    successAgents[config.providerId] = agent
                }

                val orchestrator = AIOrchestratorImpl(
                    kbRepository = EmptyKBRepository(),
                    agents = agents,
                    providerConfigs = configs.toMutableList()
                )

                val ticketId = "TEST-1"
                val result = orchestrator.analyzeTicket(ticketId, forceReanalyze = false)

                if (activeConfigs.isEmpty()) {
                    // No active providers → error result
                    assertTrue(result.context.unified.contains("Error"),
                        "Must return error when no ACTIVE providers exist")
                } else {
                    // The highest-priority ACTIVE provider should be called first
                    val expectedFirst = activeConfigs.first()
                    assertEquals(AnalysisSource.FRESH_AI, result.source,
                        "Source must be FRESH_AI when KB is empty")
                    assertTrue(result.context.unified.contains(expectedFirst.providerId),
                        "Result must come from highest-priority ACTIVE provider '${expectedFirst.providerId}', got: ${result.context.unified}")
                    assertTrue(successAgents[expectedFirst.providerId]!!.callCount > 0,
                        "Highest-priority ACTIVE provider must be called")
                }

                // Reset agents
                successAgents.values.forEach { it.reset() }
            }
        }
    }

    @Test
    fun `failover selects next ACTIVE provider when primary fails`() {
        runBlocking {
            // Fixed scenario: primary fails, secondary succeeds
            val primaryAgent = FailingAgent("primary")
            val secondaryAgent = SuccessAgent("secondary")

            val configs = mutableListOf(
                ProviderConfig("primary", "Primary", ProviderType.OLLAMA, "http://localhost:11434", priority = 0, status = ConnectionStatus.ACTIVE),
                ProviderConfig("secondary", "Secondary", ProviderType.GEMINI, "http://localhost:11435", priority = 1, status = ConnectionStatus.ACTIVE)
            )

            val orchestrator = AIOrchestratorImpl(
                kbRepository = EmptyKBRepository(),
                agents = mapOf("primary" to primaryAgent, "secondary" to secondaryAgent),
                providerConfigs = configs
            )

            val arbTicketId = Arb.bind(
                Arb.string(minSize = 2, maxSize = 5, codepoints = Codepoint.alphanumeric()),
                Arb.int(1..999)
            ) { prefix, num -> "${prefix.uppercase()}-$num" }

            checkAll(PropTestConfig(iterations = 25), arbTicketId) { ticketId ->
                primaryAgent.reset()
                secondaryAgent.reset()

                val result = orchestrator.analyzeTicket(ticketId, forceReanalyze = false)

                assertEquals(AnalysisSource.FRESH_AI, result.source,
                    "Source must be FRESH_AI since KB is empty")
                assertTrue(primaryAgent.callCount > 0,
                    "Primary agent must be tried first")
                assertTrue(secondaryAgent.callCount > 0,
                    "Secondary agent must be called after primary fails")
                assertTrue(result.context.unified.contains("secondary"),
                    "Result must come from secondary (fallback) provider")
            }
        }
    }

    @Test
    fun `all providers offline returns error result`() {
        runBlocking {
            val configs = mutableListOf(
                ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA, "http://localhost:11434", priority = 0, status = ConnectionStatus.OFFLINE),
                ProviderConfig("gemini", "Gemini", ProviderType.GEMINI, "http://localhost:11435", priority = 1, status = ConnectionStatus.OFFLINE)
            )

            val orchestrator = AIOrchestratorImpl(
                kbRepository = EmptyKBRepository(),
                agents = mapOf("ollama" to SuccessAgent("ollama"), "gemini" to SuccessAgent("gemini")),
                providerConfigs = configs
            )

            val arbTicketId = Arb.bind(
                Arb.string(minSize = 2, maxSize = 5, codepoints = Codepoint.alphanumeric()),
                Arb.int(1..999)
            ) { prefix, num -> "${prefix.uppercase()}-$num" }

            checkAll(PropTestConfig(iterations = 25), arbTicketId) { ticketId ->
                val result = orchestrator.analyzeTicket(ticketId)

                assertTrue(result.context.unified.contains("Error"),
                    "Result must contain error message when all providers are offline")
                assertEquals(AnalysisSource.FRESH_AI, result.source,
                    "Source must be FRESH_AI (error path)")
            }
        }
    }
}
