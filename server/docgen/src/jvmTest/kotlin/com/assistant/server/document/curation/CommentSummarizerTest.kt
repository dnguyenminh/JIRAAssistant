package com.assistant.server.document.curation

import com.assistant.server.document.models.FullComment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for bot comment detection patterns.
 * Requirements: 4.3
 */
class CommentSummarizerTest {

    private val summarizer = DefaultCommentSummarizer()

    @Test
    fun `ScriptRunner comments detected as bot`() {
        val comments = buildCommentList(15) { idx ->
            if (idx < 5) botComment("ScriptRunner", "Reminder: ticket pending")
            else substantiveComment("Dev $idx", "Working on implementation")
        }
        val result = summarizer.summarize(comments, hasKbRecord = false)
        assertNotNull(result.botSummary)
        assertTrue(result.botSummary!!.contains("bot comments"))
    }

    @Test
    fun `status update bots detected`() {
        val comments = buildCommentList(12) { idx ->
            if (idx < 4) botComment("Jira Automation", "Status changed from Open to In Progress")
            else substantiveComment("User $idx", "Discussed requirements")
        }
        val result = summarizer.summarize(comments, hasKbRecord = false)
        assertNotNull(result.botSummary)
    }

    @Test
    fun `10 or fewer comments included without summarization`() {
        val comments = List(8) {
            substantiveComment("User $it", "Comment body $it")
        }
        val result = summarizer.summarize(comments, hasKbRecord = false)
        assertEquals(8, result.recentComments.size)
        assertNull(result.botSummary)
    }

    @Test
    fun `empty comments returns empty summary`() {
        val result = summarizer.summarize(emptyList(), hasKbRecord = false)
        assertEquals(0, result.totalChars)
    }

    @Test
    fun `decision keywords extracted`() {
        val comments = buildCommentList(12) { idx ->
            if (idx == 10) substantiveComment("PM", "We decided to use REST API")
            else substantiveComment("Dev $idx", "Regular comment $idx")
        }
        val result = summarizer.summarize(comments, hasKbRecord = false)
        assertTrue(result.decisions.isNotEmpty())
    }

    private fun buildCommentList(
        size: Int, builder: (Int) -> FullComment
    ): List<FullComment> = List(size) { builder(it) }

    private fun botComment(author: String, body: String) = FullComment(
        author = author, createdDate = "2024-06-01T10:00:00Z", body = body
    )

    private fun substantiveComment(author: String, body: String) = FullComment(
        author = author, createdDate = "2024-06-15T10:00:00Z", body = body
    )
}
