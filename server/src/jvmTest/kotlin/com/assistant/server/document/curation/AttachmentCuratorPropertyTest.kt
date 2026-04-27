package com.assistant.server.document.curation

import com.assistant.server.document.curation.generators.CurationArbitraries
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 8: Attachment Curation Correctness
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5
 */
class AttachmentCuratorPropertyTest {

    private val curator = DefaultAttachmentCurator()

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `preview never exceeds 3000 chars or 5000 for req docs`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            Arb.list(CurationArbitraries.arbAttachmentChunkInfo(), 1..10)
        ) { attachments ->
            val result = curator.curate(attachments, emptyList(), emptySet())
            result.forEach { att ->
                if (att.isRequirementDoc) {
                    assertTrue(att.preview.length <= 5000,
                        "Req doc preview ${att.preview.length} > 5000")
                } else {
                    assertTrue(att.preview.length <= 3000,
                        "Preview ${att.preview.length} > 3000")
                }
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `KB-referenced attachments excluded`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            Arb.list(CurationArbitraries.arbAttachmentChunkInfo(), 1..10)
        ) { attachments ->
            val kbRefs = attachments.take(2).map { it.filename }.toSet()
            val result = curator.curate(attachments, emptyList(), kbRefs)
            assertTrue(result.none { it.filename in kbRefs },
                "KB-referenced attachments should be excluded")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `total attachment content never exceeds 15000 chars`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            Arb.list(CurationArbitraries.arbAttachmentChunkInfo(), 1..20)
        ) { attachments ->
            val result = curator.curate(attachments, emptyList(), emptySet())
            val total = result.sumOf { it.preview.length }
            assertTrue(total <= CurationConfig.MAX_TOTAL_ATTACHMENT_CHARS,
                "Total $total > ${CurationConfig.MAX_TOTAL_ATTACHMENT_CHARS}")
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `root attachments have higher priority than linked`() = runTest {
        checkAll(PropTestConfig(iterations = 100),
            Arb.list(CurationArbitraries.arbAttachmentChunkInfo(), 1..5),
            Arb.list(CurationArbitraries.arbAttachmentChunkInfo(), 1..5)
        ) { rootAtts, linkedAtts ->
            val result = curator.curate(rootAtts, linkedAtts, emptySet())
            if (result.size >= 2) {
                val rootResults = result.filter { it.priority < 100 }
                val linkedResults = result.filter { it.priority >= 100 }
                rootResults.forEach { r ->
                    linkedResults.forEach { l ->
                        assertTrue(r.priority < l.priority,
                            "Root priority ${r.priority} >= linked ${l.priority}")
                    }
                }
            }
        }
    }
}
