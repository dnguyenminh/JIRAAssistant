package com.assistant.server.ai

import com.assistant.ai.*
import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.kb.EvolutionEntry
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 4: KB-First Strategy — Cache Hit Avoids AI Call
 *
 * For any ticketId already in Knowledge_Base, calling analyzeTicket(ticketId, forceReanalyze=false)
 * SHALL return result from KB cache with source = KB_CACHE without calling any AI agent.
 *
 * **Validates: Requirements 5.2, 9.3**
 *
 * Feature: jira-assistant-app, Property 4: KB-First Strategy — Cache Hit Avoids AI Call
 */
@OptIn(ExperimentalKotest::class)
class KBFirstStrategyPropertyTest {

    /**
     * Fake KBRepository that stores records in memory.
     */
    class FakeKBRepository : KBRepository {
        private val store = mutableMapOf<String, KBRecord>()

        fun seed(record: KBRecord) {
            store[record.ticketId] = record
        }

        override suspend fun findByTicketId(ticketId: String): KBRecord? = store[ticketId]
        override suspend fun save(record: KBRecord): Boolean {
            store[record.ticketId] = record
            return true
        }
        override suspend fun overwrite(record: KBRecord): Boolean {
            store[record.ticketId] = record
            return true
        }
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }

    /**
     * Spy AIAgent that tracks whether analyze() was called.
     */
    class SpyAIAgent : AIAgent {
        var callCount = 0
            private set

        override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
            callCount++
            return AIResult.Success("""{"requirementSummary":{"unified":"test"},"evolution":[],"complexity":{"scrumPoints":3.0,"description":"test"}}""")
        }

        override fun getAgentName(): String = "SpyAgent"

        fun reset() { callCount = 0 }
    }

    private fun arbSafeString(min: Int = 1, max: Int = 30): Arb<String> =
        Arb.string(minSize = min, maxSize = max, codepoints = Codepoint.alphanumeric())

    private val arbTicketId: Arb<String> = Arb.bind(
        Arb.string(minSize = 2, maxSize = 6, codepoints = Codepoint.alphanumeric()),
        Arb.int(1..9999)
    ) { prefix, num -> "${prefix.uppercase()}-$num" }

    private val arbKBRecord: Arb<KBRecord> = Arb.bind(
        arbTicketId,
        arbSafeString(5, 100),
        Arb.double(0.0..40.0),
        Arb.double(0.0..1.0),
        arbSafeString(5, 50)
    ) { ticketId, summary, points, confidence, rationale ->
        KBRecord(
            ticketId = ticketId,
            requirementSummary = summary,
            evolutionHistory = listOf(
                EvolutionEntry("1.0", "2024-01-01", "Initial", "ORIGIN")
            ),
            scrumPoints = points,
            confidenceScore = confidence,
            rationale = rationale,
            similarTicketRefs = emptyList(),
            timestamp = "2024-01-01T00:00:00Z"
        )
    }

    @Test
    fun `KB cache hit returns KB_CACHE source without calling AI agent`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbKBRecord) { record ->
                val fakeKB = FakeKBRepository()
                fakeKB.seed(record)

                val spyAgent = SpyAIAgent()
                val orchestrator = AIOrchestratorImpl(
                    kbRepository = fakeKB,
                    agents = mapOf("ollama" to spyAgent),
                    providerConfigs = mutableListOf(
                        ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA, "http://localhost:11434", priority = 0, status = ConnectionStatus.ACTIVE)
                    )
                )

                val result = orchestrator.analyzeTicket(record.ticketId, forceReanalyze = false)

                assertEquals(AnalysisSource.KB_CACHE, result.source,
                    "Source must be KB_CACHE when ticket exists in KB")
                assertEquals(record.ticketId, result.ticketId,
                    "ticketId must match the requested ticket")
                assertEquals(record.requirementSummary, result.context.unified,
                    "Requirement summary must come from KB record")
                assertEquals(0, spyAgent.callCount,
                    "AI agent must NOT be called when KB cache hit occurs")
            }
        }
    }

    @Test
    fun `forceReanalyze=true calls AI even when KB has cached record`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbKBRecord) { record ->
                val fakeKB = FakeKBRepository()
                fakeKB.seed(record)

                val spyAgent = SpyAIAgent()
                val orchestrator = AIOrchestratorImpl(
                    kbRepository = fakeKB,
                    agents = mapOf("ollama" to spyAgent),
                    providerConfigs = mutableListOf(
                        ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA, "http://localhost:11434", priority = 0, status = ConnectionStatus.ACTIVE)
                    )
                )

                val result = orchestrator.analyzeTicket(record.ticketId, forceReanalyze = true)

                assertEquals(AnalysisSource.FRESH_AI, result.source,
                    "Source must be FRESH_AI when forceReanalyze=true")
                assertTrue(spyAgent.callCount > 0,
                    "AI agent MUST be called when forceReanalyze=true")

                spyAgent.reset()
            }
        }
    }
}
