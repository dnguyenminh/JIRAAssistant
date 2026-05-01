package com.assistant.server.chat

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import com.assistant.chat.*
import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.GraphLayout
import com.assistant.graph.Position
import com.assistant.graph.Bounds
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatServiceImplTest {

    private lateinit var chatService: ChatServiceImpl
    private lateinit var fakeAgent: FakeAIAgent
    private lateinit var fakeKBRepo: FakeKBRepository
    private lateinit var fakeGraphEngine: FakeGraphEngine

    @BeforeEach
    fun setup() {
        fakeAgent = FakeAIAgent()
        fakeKBRepo = FakeKBRepository()
        fakeGraphEngine = FakeGraphEngine()
        chatService = ChatServiceImpl(
            aiAgentProvider = { fakeAgent },
            kbRepository = fakeKBRepo,
            graphEngine = fakeGraphEngine
        )
    }

    // --- processChat tests ---

    @Test
    fun `processChat returns parsed JSON response from AI`() = runBlocking {
        fakeAgent.nextResponse = AIResult.Success("""{"reply":"Hello!","actions":[],"references":[]}""")
        val ctx = ChatContext("PROJ", "dashboard", "READER", "user1")

        val result = chatService.processChat("Hi", ctx, emptyList())

        assertEquals("Hello!", result.reply)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `processChat returns plain text fallback on non-JSON AI response`() = runBlocking {
        fakeAgent.nextResponse = AIResult.Success("Just a plain text answer")
        val ctx = ChatContext("PROJ", "dashboard", "READER", "user1")

        val result = chatService.processChat("Hi", ctx, emptyList())

        assertEquals("Just a plain text answer", result.reply)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `processChat returns error message on AI failure`() = runBlocking {
        fakeAgent.nextResponse = AIResult.Failure("Provider offline")
        val ctx = ChatContext("PROJ", "dashboard", "READER", "user1")

        val result = chatService.processChat("Hi", ctx, emptyList())

        assertTrue(result.reply.contains("Provider offline"))
    }

    @Test
    fun `processChat includes KB context when ticket IDs mentioned`() = runBlocking {
        fakeKBRepo.records["PROJ-123"] = KBRecord(
            ticketId = "PROJ-123",
            requirementSummary = "Login feature",
            evolutionHistory = emptyList(),
            scrumPoints = 5.0,
            confidenceScore = 0.9,
            rationale = "Medium complexity",
            similarTicketRefs = emptyList(),
            timestamp = "2024-01-01"
        )
        fakeAgent.nextResponse = AIResult.Success("Got it")
        val ctx = ChatContext("PROJ", "dashboard", "READER", "user1")

        chatService.processChat("Tell me about PROJ-123", ctx, emptyList())

        // Verify the prompt sent to AI contains KB context
        assertTrue(fakeAgent.lastPrompt!!.contains("PROJ-123"))
        assertTrue(fakeAgent.lastPrompt!!.contains("Login feature"))
    }

    // --- buildSystemPrompt tests ---

    @Test
    fun `buildSystemPrompt includes project key and screen`() {
        val ctx = ChatContext("MYPROJ", "knowledge_graph", "ADMINISTRATOR", "admin1")

        val prompt = chatService.buildSystemPrompt(ctx)

        assertTrue(prompt.contains("MYPROJ"))
        assertTrue(prompt.contains("knowledge_graph"))
        assertTrue(prompt.contains("ADMINISTRATOR"))
        assertTrue(prompt.contains("JSON"))
    }

    // --- buildKBContext tests ---

    @Test
    fun `buildKBContext returns no data message when no ticket IDs in message`() = runBlocking {
        val result = chatService.buildKBContext("PROJ", "Hello, how are you?")
        assertEquals("No KB data.", result)
    }

    @Test
    fun `buildKBContext extracts ticket IDs and queries KB`() = runBlocking {
        fakeKBRepo.records["ABC-42"] = KBRecord(
            ticketId = "ABC-42",
            requirementSummary = "Auth module",
            evolutionHistory = emptyList(),
            scrumPoints = 3.0,
            confidenceScore = 0.85,
            rationale = "Simple",
            similarTicketRefs = emptyList(),
            timestamp = "2024-01-01"
        )

        val result = chatService.buildKBContext("ABC", "Check ABC-42 and ABC-99")

        assertTrue(result.contains("ABC-42"))
        assertTrue(result.contains("Auth module"))
        // ABC-99 not in KB, so only ABC-42 appears
        assertFalse(result.contains("ABC-99"))
    }

    @Test
    fun `buildKBContext returns no data when ticket IDs not found in KB`() = runBlocking {
        val result = chatService.buildKBContext("PROJ", "Look at PROJ-999")
        assertEquals("No KB data.", result)
    }



    // --- buildGraphContext tests ---

    @Test
    fun `buildGraphContext returns no data message when no graph exists`() = runBlocking {
        val result = chatService.buildGraphContext("PROJ")
        assertEquals("No graph data.", result)
    }

    @Test
    fun `buildGraphContext returns summary with node and edge counts`() = runBlocking {
        val graph = NetworkGraph(
            nodes = listOf(
                TicketNode("1", "PROJ-1", "Task 1", "Open"),
                TicketNode("2", "PROJ-2", "Task 2", "Done")
            ),
            edges = listOf(TicketEdge("1", "2", "related"))
        )
        fakeKBRepo.graphs["PROJ"] = graph
        fakeGraphEngine.clustersToReturn = listOf(
            Cluster(1, listOf("1", "2"), "#ff0000")
        )

        val result = chatService.buildGraphContext("PROJ")

        assertEquals("Graph: 2 nodes, 1 edges, 1 clusters", result)
    }

    // --- formatHistory tests (via processChat round-trip) ---
    // formatHistory is private — tested indirectly through processChat

    // --- parseAIResponse tests ---

    @Test
    fun `parseAIResponse decodes valid JSON`() {
        val jsonStr = """{"reply":"Answer","actions":[{"type":"navigate","label":"Go","params":{"screen":"dashboard"}}],"references":[{"type":"ticket","id":"T-1","label":"Ticket 1"}]}"""

        val result = chatService.parseAIResponse(jsonStr, 0)

        assertEquals("Answer", result.reply)
        assertEquals(1, result.actions.size)
        assertEquals("navigate", result.actions[0].type)
        assertEquals(1, result.references.size)
        assertEquals("T-1", result.references[0].id)
    }

    @Test
    fun `parseAIResponse strips markdown code fences`() {
        val wrapped = "```json\n{\"reply\":\"Fenced\",\"actions\":[],\"references\":[]}\n```"

        val result = chatService.parseAIResponse(wrapped, 0)

        assertEquals("Fenced", result.reply)
    }

    @Test
    fun `parseAIResponse falls back to plain text on invalid JSON`() {
        val result = chatService.parseAIResponse("Not JSON at all", 0)

        assertEquals("Not JSON at all", result.reply)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.references.isEmpty())
    }

    // --- Fake implementations ---

    private class FakeAIAgent : AIAgent {
        var nextResponse: AIResult = AIResult.Success("default")
        var lastPrompt: String? = null

        override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
            lastPrompt = prompt
            return nextResponse
        }

        override fun getAgentName(): String = "FakeAgent"
    }

    private class FakeKBRepository : KBRepository {
        val records = mutableMapOf<String, KBRecord>()
        val graphs = mutableMapOf<String, NetworkGraph>()

        override suspend fun findByTicketId(ticketId: String): KBRecord? = records[ticketId]
        override suspend fun save(record: KBRecord): Boolean = true
        override suspend fun overwrite(record: KBRecord): Boolean = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = graphs[projectKey]
    }

    private class FakeGraphEngine : GraphEngine {
        var clustersToReturn: List<Cluster> = emptyList()

        override fun computeLayout(graph: NetworkGraph, width: Double, height: Double): GraphLayout {
            return GraphLayout(emptyMap(), Bounds(width, height))
        }

        override fun detectClusters(graph: NetworkGraph): List<Cluster> = clustersToReturn
    }
}
