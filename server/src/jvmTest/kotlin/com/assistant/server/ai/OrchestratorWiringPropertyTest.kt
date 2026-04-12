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
 * Verify AIOrchestratorImpl wiring: providerConfigProvider
 * must be used for provider selection, not just the static list.
 * This catches the bug where empty providerConfigs = "All AI providers offline".
 */
@OptIn(ExperimentalKotest::class)
class OrchestratorWiringPropertyTest {

    class EmptyKB : KBRepository {
        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }

    class StubAgent(private val name: String) : AIAgent {
        override suspend fun analyze(prompt: String, context: AIContext?): AIResult =
            AIResult.Success("""{"requirementSummary":{"unified":"from $name"},"evolution":[],"complexity":{"scrumPoints":3.0,"description":"ok"}}""")
        override fun getAgentName() = name
    }

    @Test
    fun `orchestrator with empty static configs but valid providerConfigProvider still works`() {
        runBlocking {
            // Simulate ServerModule bug: empty providerConfigs, but providerConfigProvider returns data
            val orchestrator = AIOrchestratorImpl(
                kbRepository = EmptyKB(),
                agents = emptyMap(),
                agentProvider = { mapOf("ollama" to StubAgent("ollama")) },
                providerConfigs = mutableListOf(), // empty — the old bug
                providerConfigProvider = {
                    listOf(
                        ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA,
                            "http://localhost:11434", priority = 0,
                            status = ConnectionStatus.ACTIVE)
                    )
                }
            )

            val result = orchestrator.analyzeTicket("TEST-1")
            assertFalse(result.context.unified.contains("Error"),
                "Must NOT return error when providerConfigProvider has ACTIVE providers")
            assertTrue(result.context.unified.contains("from ollama"),
                "Must use agent from providerConfigProvider")
        }
    }

    @Test
    fun `orchestrator with empty configs and no providerConfigProvider returns error`() {
        runBlocking {
            val orchestrator = AIOrchestratorImpl(
                kbRepository = EmptyKB(),
                agents = mapOf("ollama" to StubAgent("ollama")),
                providerConfigs = mutableListOf() // empty, no provider
            )

            val result = orchestrator.analyzeTicket("TEST-1")
            assertTrue(result.context.unified.contains("Error"),
                "Must return error when no ACTIVE providers exist")
        }
    }

    @Test
    fun `providerConfigProvider is called fresh each time`() {
        runBlocking {
            var callCount = 0
            val orchestrator = AIOrchestratorImpl(
                kbRepository = EmptyKB(),
                agents = emptyMap(),
                agentProvider = { mapOf("ollama" to StubAgent("ollama")) },
                providerConfigProvider = {
                    callCount++
                    listOf(
                        ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA,
                            "http://localhost:11434", priority = 0,
                            status = ConnectionStatus.ACTIVE)
                    )
                }
            )

            orchestrator.analyzeTicket("T-1")
            orchestrator.analyzeTicket("T-2")
            assertTrue(callCount >= 2,
                "providerConfigProvider must be called on each analyzeTicket")
        }
    }

    @Test
    fun `dynamic config change from OFFLINE to ACTIVE is picked up`() {
        runBlocking {
            var isActive = false
            val orchestrator = AIOrchestratorImpl(
                kbRepository = EmptyKB(),
                agents = emptyMap(),
                agentProvider = { mapOf("ollama" to StubAgent("ollama")) },
                providerConfigProvider = {
                    val status = if (isActive) ConnectionStatus.ACTIVE else ConnectionStatus.OFFLINE
                    listOf(
                        ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA,
                            "http://localhost:11434", priority = 0, status = status)
                    )
                }
            )

            // First call: OFFLINE → error
            val r1 = orchestrator.analyzeTicket("T-1")
            assertTrue(r1.context.unified.contains("Error"))

            // Change to ACTIVE
            isActive = true
            val r2 = orchestrator.analyzeTicket("T-2")
            assertFalse(r2.context.unified.contains("Error"),
                "After config change to ACTIVE, analysis must succeed")
        }
    }
}
