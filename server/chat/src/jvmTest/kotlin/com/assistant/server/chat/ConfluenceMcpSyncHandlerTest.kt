package com.assistant.server.chat

import com.assistant.server.attachment.models.ChunkType
import com.assistant.server.indexing.EmbedItem
import com.assistant.server.indexing.IndexingPipeline
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceMcpSyncHandlerTest {

    private lateinit var handler: ConfluenceMcpSyncHandler
    private lateinit var fakeVectorStore: FakeVectorStore
    private lateinit var pipeline: IndexingPipeline

    @BeforeEach
    fun setup() {
        fakeVectorStore = FakeVectorStore(emptyList())
        pipeline = IndexingPipeline(null, fakeVectorStore)
        handler = ConfluenceMcpSyncHandler(pipeline)
    }

    // --- isConfluenceTool ---

    @Test
    fun `isConfluenceTool returns true for known tools`() {
        assertTrue(handler.isConfluenceTool("search_confluence"))
        assertTrue(handler.isConfluenceTool("confluence_search"))
        assertTrue(handler.isConfluenceTool("get_page"))
        assertTrue(handler.isConfluenceTool("get_confluence_page"))
    }

    @Test
    fun `isConfluenceTool returns true for tools containing confluence`() {
        assertTrue(handler.isConfluenceTool("my_confluence_tool"))
        assertTrue(handler.isConfluenceTool("CONFLUENCE_SEARCH_V2"))
    }

    @Test
    fun `isConfluenceTool returns false for non-confluence tools`() {
        assertFalse(handler.isConfluenceTool("create_issue"))
        assertFalse(handler.isConfluenceTool("search_issues"))
    }

    // --- extractPages ---

    @Test
    fun `extractPages parses single page object`() {
        val json = """{"id":"123","title":"Auth Guide","url":"https://wiki.example.com/auth","excerpt":"How to authenticate"}"""
        val pages = handler.extractPages(json)
        assertEquals(1, pages.size)
        assertEquals("Auth Guide", pages[0].title)
        assertEquals("https://wiki.example.com/auth", pages[0].url)
        assertEquals("How to authenticate", pages[0].summary)
    }

    @Test
    fun `extractPages parses results array`() {
        val json = """{"results":[{"id":"1","title":"Page A","url":"https://a.com"},{"id":"2","title":"Page B","url":"https://b.com"}]}"""
        val pages = handler.extractPages(json)
        assertEquals(2, pages.size)
        assertEquals("Page A", pages[0].title)
        assertEquals("Page B", pages[1].title)
    }

    @Test
    fun `extractPages parses top-level array`() {
        val json = """[{"id":"1","title":"Doc 1"},{"id":"2","title":"Doc 2"}]"""
        val pages = handler.extractPages(json)
        assertEquals(2, pages.size)
    }

    @Test
    fun `extractPages returns empty for non-JSON text`() {
        val pages = handler.extractPages("No results found")
        assertTrue(pages.isEmpty())
    }

    @Test
    fun `extractPages handles missing url gracefully`() {
        val json = """{"id":"1","title":"No URL Page","excerpt":"Summary"}"""
        val pages = handler.extractPages(json)
        assertEquals(1, pages.size)
        assertNull(pages[0].url)
        assertEquals("Summary", pages[0].summary)
    }

    @Test
    fun `extractPages uses _links webui as fallback url`() {
        val json = """{"id":"1","title":"Page","_links":{"webui":"https://wiki.example.com/page"}}"""
        val pages = handler.extractPages(json)
        assertEquals("https://wiki.example.com/page", pages[0].url)
    }

    @Test
    fun `extractPages skips entries without title`() {
        val json = """[{"id":"1","url":"https://a.com"},{"id":"2","title":"Valid"}]"""
        val pages = handler.extractPages(json)
        assertEquals(1, pages.size)
        assertEquals("Valid", pages[0].title)
    }

    // --- formatConfluenceText ---

    @Test
    fun `formatConfluenceText includes summary when present`() {
        val page = ConfluencePage("1", "Auth Guide", null, "How to auth")
        val text = ConfluenceMcpSyncHandler.formatConfluenceText(page)
        assertEquals("[Confluence] Auth Guide. How to auth", text)
    }

    @Test
    fun `formatConfluenceText omits summary when blank`() {
        val page = ConfluencePage("1", "Auth Guide", null, "")
        val text = ConfluenceMcpSyncHandler.formatConfluenceText(page)
        assertEquals("[Confluence] Auth Guide", text)
    }

    // --- processToolResult ---

    @Test
    fun `processToolResult returns extracted pages`() = runBlocking {
        val json = """{"results":[{"id":"1","title":"Doc","url":"https://doc.com","excerpt":"Info"}]}"""
        val pages = handler.processToolResult("PROJ", json)
        assertEquals(1, pages.size)
        assertEquals("Doc", pages[0].title)
    }

    @Test
    fun `processToolResult returns empty for invalid input`() = runBlocking {
        val pages = handler.processToolResult("PROJ", "not json")
        assertTrue(pages.isEmpty())
    }

    @Test
    fun `processToolResult works without indexing pipeline`() = runBlocking {
        val handlerNoPipeline = ConfluenceMcpSyncHandler(null)
        val json = """{"id":"1","title":"Page","url":"https://x.com"}"""
        val pages = handlerNoPipeline.processToolResult("PROJ", json)
        assertEquals(1, pages.size)
    }
}
