package com.assistant.server.chat

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.chat.models.SyncType
import com.assistant.server.indexing.IndexingPipeline
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JiraMcpSyncHandlerTest {

    private lateinit var handler: JiraMcpSyncHandler
    private lateinit var kbRepo: InMemoryKBRepository
    private lateinit var fakeVectorStore: FakeVectorStore

    @BeforeEach
    fun setup() {
        kbRepo = InMemoryKBRepository()
        fakeVectorStore = FakeVectorStore(emptyList())
        val pipeline = IndexingPipeline(null, fakeVectorStore)
        handler = JiraMcpSyncHandler(kbRepo, pipeline)
    }

    // --- isJiraTool ---

    @Test
    fun `isJiraTool returns true for create_issue`() {
        assertTrue(handler.isJiraTool("create_issue"))
    }

    @Test
    fun `isJiraTool returns true for update_issue`() {
        assertTrue(handler.isJiraTool("update_issue"))
    }

    @Test
    fun `isJiraTool returns true for link_issues`() {
        assertTrue(handler.isJiraTool("link_issues"))
    }

    @Test
    fun `isJiraTool returns false for unknown tool`() {
        assertFalse(handler.isJiraTool("search_issues"))
    }

    // --- detectSyncType ---

    @Test
    fun `detectSyncType returns CREATE_TICKET for create tools`() {
        assertEquals(SyncType.CREATE_TICKET, handler.detectSyncType("create_issue"))
        assertEquals(SyncType.CREATE_TICKET, handler.detectSyncType("jira_create_issue"))
    }

    @Test
    fun `detectSyncType returns UPDATE_TICKET for update tools`() {
        assertEquals(SyncType.UPDATE_TICKET, handler.detectSyncType("update_issue"))
    }

    @Test
    fun `detectSyncType returns LINK_TICKETS for link tools`() {
        assertEquals(SyncType.LINK_TICKETS, handler.detectSyncType("link_issues"))
    }

    @Test
    fun `detectSyncType returns NONE for unknown tool`() {
        assertEquals(SyncType.NONE, handler.detectSyncType("get_issue"))
    }

    // --- parseToolResult ---

    @Test
    fun `parseToolResult extracts JSON from text`() {
        val result = handler.parseToolResult("""Created: {"key":"PROJ-99","id":"99","summary":"New bug"}""")
        assertNotNull(result)
        assertEquals("PROJ-99", result!!["key"]?.toString()?.trim('"'))
    }

    @Test
    fun `parseToolResult returns null for non-JSON text`() {
        assertNull(handler.parseToolResult("Success: ticket created"))
    }

    // --- handleCreateTicket ---

    @Test
    fun `handleCreateTicket adds node to graph`() = runBlocking {
        val graph = NetworkGraph(
            nodes = listOf(TicketNode("1", "PROJ-1", "Existing", "Open")),
            edges = emptyList()
        )
        kbRepo.graphs["PROJ"] = graph

        val json = handler.parseToolResult("""{"key":"PROJ-99","id":"99","summary":"New ticket","status":"Open"}""")
        val result = handler.handleCreateTicket("PROJ", json)

        assertTrue(result.success)
        assertEquals(SyncType.CREATE_TICKET, result.syncType)
        assertEquals("PROJ-99", result.ticketKey)
        val updated = kbRepo.graphs["PROJ"]!!
        assertEquals(2, updated.nodes.size)
        assertTrue(updated.nodes.any { it.key == "PROJ-99" })
    }

    @Test
    fun `handleCreateTicket skips duplicate node`() = runBlocking {
        val graph = NetworkGraph(
            nodes = listOf(TicketNode("99", "PROJ-99", "Existing", "Open")),
            edges = emptyList()
        )
        kbRepo.graphs["PROJ"] = graph

        val json = handler.parseToolResult("""{"key":"PROJ-99","id":"99","summary":"Dup"}""")
        handler.handleCreateTicket("PROJ", json)

        assertEquals(1, kbRepo.graphs["PROJ"]!!.nodes.size)
    }

    @Test
    fun `handleCreateTicket returns warning when no key in response`() = runBlocking {
        val result = handler.handleCreateTicket("PROJ", handler.parseToolResult("""{"status":"ok"}"""))
        assertFalse(result.success)
        assertNotNull(result.warningMessage)
    }

    // --- handleUpdateTicket ---

    @Test
    fun `handleUpdateTicket updates node attributes`() = runBlocking {
        val graph = NetworkGraph(
            nodes = listOf(TicketNode("1", "PROJ-1", "Old summary", "Open")),
            edges = emptyList()
        )
        kbRepo.graphs["PROJ"] = graph

        val json = handler.parseToolResult("""{"key":"PROJ-1","id":"1","summary":"New summary","status":"Done"}""")
        val result = handler.handleUpdateTicket("PROJ", json)

        assertTrue(result.success)
        val updated = kbRepo.graphs["PROJ"]!!.nodes.first()
        assertEquals("New summary", updated.summary)
        assertEquals("Done", updated.status)
    }

    // --- handleLinkTickets ---

    @Test
    fun `handleLinkTickets adds edge between existing nodes`() = runBlocking {
        val graph = NetworkGraph(
            nodes = listOf(
                TicketNode("1", "PROJ-1", "Task 1", "Open"),
                TicketNode("2", "PROJ-2", "Task 2", "Open")
            ),
            edges = emptyList()
        )
        kbRepo.graphs["PROJ"] = graph

        val json = handler.parseToolResult(
            """{"inwardIssue":{"key":"PROJ-1"},"outwardIssue":{"key":"PROJ-2"},"type":{"name":"blocks"}}"""
        )
        val result = handler.handleLinkTickets("PROJ", json)

        assertTrue(result.success)
        assertEquals(SyncType.LINK_TICKETS, result.syncType)
        val updated = kbRepo.graphs["PROJ"]!!
        assertEquals(1, updated.edges.size)
        assertEquals("blocks", updated.edges[0].relationshipType)
    }

    @Test
    fun `handleLinkTickets skips when target node not in graph`() = runBlocking {
        val graph = NetworkGraph(
            nodes = listOf(TicketNode("1", "PROJ-1", "Task 1", "Open")),
            edges = emptyList()
        )
        kbRepo.graphs["PROJ"] = graph

        val json = handler.parseToolResult(
            """{"inwardIssue":{"key":"PROJ-1"},"outwardIssue":{"key":"PROJ-999"},"type":{"name":"blocks"}}"""
        )
        handler.handleLinkTickets("PROJ", json)

        assertEquals(0, kbRepo.graphs["PROJ"]!!.edges.size)
    }

    // --- syncAfterToolCall end-to-end ---

    @Test
    fun `syncAfterToolCall returns NONE for non-Jira tool`() = runBlocking {
        val result = handler.syncAfterToolCall("PROJ", "search_issues", "{}")
        assertTrue(result.success)
        assertEquals(SyncType.NONE, result.syncType)
    }

    @Test
    fun `syncAfterToolCall handles exception gracefully`() = runBlocking {
        // No graph data → addNodeToGraph silently skips, but parsing works
        val result = handler.syncAfterToolCall(
            "PROJ", "create_issue",
            """{"key":"PROJ-5","id":"5","summary":"Test"}"""
        )
        // No graph exists, so node not added, but no exception
        assertTrue(result.success)
    }

    // --- In-memory KBRepository for tests ---

    private class InMemoryKBRepository : KBRepository {
        val records = mutableMapOf<String, KBRecord>()
        val graphs = mutableMapOf<String, NetworkGraph>()

        override suspend fun findByTicketId(ticketId: String) = records[ticketId]
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
            graphs[projectKey] = graph; return true
        }
        override suspend fun getGraphData(projectKey: String) = graphs[projectKey]
    }
}
