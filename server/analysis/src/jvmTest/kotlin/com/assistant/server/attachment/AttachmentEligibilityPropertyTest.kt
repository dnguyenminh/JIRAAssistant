package com.assistant.server.attachment

import com.assistant.jira.JiraAttachment
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property 2: Attachment eligibility filter
 * An attachment is eligible iff its extension ∈ SUPPORTED_EXTENSIONS
 * AND its size ≤ MAX_FILE_SIZE (50MB).
 * Validates: Requirements 22.3, 22.4
 */
@OptIn(ExperimentalKotest::class)
class AttachmentEligibilityPropertyTest {

    private val pipeline = AttachmentPipeline(
        downloader = FakeDownloader(),
        embeddingService = FakeEmbeddingService(),
        vectorStore = FakeVectorStore(),
        mcpProcessManager = FakeMcpProcessManager(),
        scanLogRepository = FakeScanLogRepository(),
        jiraAuthProvider = { null }
    )

    private val supportedExts = AttachmentPipeline.SUPPORTED_EXTENSIONS.toList()
    private val unsupportedExts = listOf("exe", "zip", "rar", "bin", "iso", "dmg", "apk", "jar")

    @Test
    fun `any extension within size limit is eligible`() {
        runBlocking {
            val allExts = supportedExts + unsupportedExts
            checkAll(PropTestConfig(iterations = 200),
                Arb.element(allExts),
                Arb.long(1L..AttachmentPipeline.MAX_FILE_SIZE)
            ) { ext, size ->
                val att = JiraAttachment(id = "1", filename = "file.$ext", size = size)
                assertTrue(pipeline.isEligible(att),
                    "file.$ext (${size}B) should be eligible (no extension filter)")
            }
        }
    }

    @Test
    fun `file exceeding MAX_FILE_SIZE is not eligible`() {
        runBlocking {
            val overSize = AttachmentPipeline.MAX_FILE_SIZE + 1
            val allExts = supportedExts + unsupportedExts
            checkAll(PropTestConfig(iterations = 25),
                Arb.element(allExts),
                Arb.long(overSize..overSize + 100_000_000L)
            ) { ext, size ->
                val att = JiraAttachment(id = "1", filename = "file.$ext", size = size)
                assertFalse(pipeline.isEligible(att),
                    "file.$ext (${size}B) should NOT be eligible (too large)")
            }
        }
    }

    @Test
    fun `no extension is still eligible if within size limit`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25),
                Arb.string(minSize = 3, maxSize = 10, codepoints = Codepoint.alphanumeric())
            ) { name ->
                val att = JiraAttachment(id = "1", filename = name, size = 1024) // no dot
                assertTrue(pipeline.isEligible(att),
                    "'$name' (no extension) should be eligible (no extension filter)")
            }
        }
    }

    @Test
    fun `getExtension extracts lowercase extension correctly`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 200),
                Arb.element(supportedExts + unsupportedExts)
            ) { ext ->
                val upper = ext.uppercase()
                assertEquals(ext, pipeline.getExtension("file.$upper"),
                    "Extension must be extracted as lowercase")
            }
        }
    }
}
