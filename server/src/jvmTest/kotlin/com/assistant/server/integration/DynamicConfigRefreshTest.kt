package com.assistant.server.integration

import com.assistant.ai.*
import com.assistant.domain.NetworkGraph
import com.assistant.graph.Bounds
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.GraphLayout
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.chat.ChatServiceImpl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration tests verifying dynamic config refresh.
 * Catches bugs where factory lambdas don't pick up DB changes.
 */
class DynamicConfigRefreshTest {

    // --- Fakes ---

    class TrackingAgent(private val name: String) : AIAgent {
        var lastPrompt: String? = null
        override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
            lastPrompt = prompt
            return AIResult.Success("""{"reply":"from $name"}""")
        }
        override fun getAgentName() = name
    }

    class EmptyKB : KBRepository {
        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }

    class StubGraph : GraphEngine {
        override fun computeLayout(graph: NetworkGraph, width: Double, height: Double) =
            GraphLayout(emptyMap(), Bounds(width, height))
        override fun detectClusters(graph: NetworkGraph) = emptyList<Cluster>()
    }

    // --- Tests ---

    @Test
    fun `ChatService aiAgentProvider is called fresh each time`() = runBlocking {
        var callCount = 0
        val agents = listOf(TrackingAgent("agent-1"), TrackingAgent("agent-2"))

        val service = ChatServiceImpl(
            aiAgentProvider = {
                val agent = agents[callCount.coerceAtMost(1)]
                callCount++
                agent
            },
            kbRepository = EmptyKB(),
            graphEngine = StubGraph()
        )

        val ctx = com.assistant.chat.ChatContext("PROJ", "dashboard", "ADMIN", "admin")
        service.processChat("hello", ctx, emptyList())
        service.processChat("world", ctx, emptyList())

        assertEquals(2, callCount,
            "aiAgentProvider must be called on each processChat, not cached")
    }

    @Test
    fun `AIOrchestrator providerConfigProvider called on each analyzeTicket`() = runBlocking {
        var configCallCount = 0
        val orchestrator = AIOrchestratorImpl(
            kbRepository = EmptyKB(),
            agents = emptyMap(),
            agentProvider = {
                mapOf("test" to TrackingAgent("test"))
            },
            providerConfigProvider = {
                configCallCount++
                listOf(ProviderConfig(
                    "test", "Test", ProviderType.OLLAMA,
                    "http://localhost:11434", priority = 0,
                    status = ConnectionStatus.ACTIVE
                ))
            }
        )

        orchestrator.analyzeTicket("T-1")
        orchestrator.analyzeTicket("T-2")

        assertTrue(configCallCount >= 2,
            "providerConfigProvider must be called on each analyzeTicket")
    }

    @Test
    fun `AIOrchestrator picks up new provider added after init`() = runBlocking {
        val providers = mutableListOf(
            ProviderConfig("p1", "P1", ProviderType.OLLAMA,
                "http://localhost:11434", priority = 0,
                status = ConnectionStatus.ACTIVE)
        )
        val agentMap = mutableMapOf<String, AIAgent>(
            "p1" to TrackingAgent("p1")
        )

        val orchestrator = AIOrchestratorImpl(
            kbRepository = EmptyKB(),
            agents = emptyMap(),
            agentProvider = { agentMap.toMap() },
            providerConfigProvider = { providers.toList() }
        )

        // First call uses p1
        val r1 = orchestrator.analyzeTicket("T-1")
        assertFalse(r1.context.unified.contains("Error"))

        // Add p2 dynamically
        providers.add(ProviderConfig("p2", "P2", ProviderType.OLLAMA,
            "http://localhost:11435", priority = 1,
            status = ConnectionStatus.ACTIVE))
        agentMap["p2"] = TrackingAgent("p2")

        // Disable p1
        providers[0] = providers[0].copy(status = ConnectionStatus.OFFLINE)

        // Second call should use p2
        val r2 = orchestrator.analyzeTicket("T-2")
        assertFalse(r2.context.unified.contains("Error"),
            "Must use newly added provider p2 after p1 goes offline")
    }
}
