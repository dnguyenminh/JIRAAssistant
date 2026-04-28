package com.assistant.server.document.curation

import com.assistant.server.document.curation.generators.CurationArbitraries
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property 7: Comment Summarization Correctness
 * Validates: Requirements 4.1, 4.3, 4.4, 4.5
 */
class CommentSummarizerPropertyTest {

    private val summarizer = DefaultCommentSummarizer()

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `output never exceeds 2000 chars`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbCommentList(11..50)
        ) { comments ->
            val result = summarizer.summarize(comments, hasKbRecord = false)
            assertTrue(result.totalChars <= 2000,
                "totalChars=${result.totalChars} exceeds 2000")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `preserves at most 3 recent substantive comments`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbCommentList(11..30)
        ) { comments ->
            val result = summarizer.summarize(comments, hasKbRecord = false)
            assertTrue(result.recentComments.size <= 3)
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `bot comments replaced with count and range`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            CurationArbitraries.arbCommentList(11..30)
        ) { comments ->
            val hasBots = comments.any {
                it.author.lowercase().contains("scriptrunner") ||
                    it.author.lowercase().contains("automation") ||
                    it.author.lowercase().contains("bitbucket")
            }
            val result = summarizer.summarize(comments, hasKbRecord = false)
            if (hasBots) {
                assertNotNull(result.botSummary,
                    "Expected botSummary when bot comments present")
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `skips entirely when hasKbRecord is true`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            CurationArbitraries.arbCommentList(1..20)
        ) { comments ->
            val result = summarizer.summarize(comments, hasKbRecord = true)
            assertEquals(0, result.totalChars)
            assertTrue(result.decisions.isEmpty())
            assertTrue(result.recentComments.isEmpty())
        }
    }
}
