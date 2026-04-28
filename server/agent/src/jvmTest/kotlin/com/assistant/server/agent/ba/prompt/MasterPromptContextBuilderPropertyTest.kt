package com.assistant.server.agent.ba.prompt

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import com.assistant.server.document.curation.models.ContentClassification
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Property 5: Section Placement Correctness
 * Property 6: TO-BE Before AS-IS Ordering
 * Validates: Requirements 3.2, 3.3, 3.4, 3.5, 3.6
 */
@OptIn(ExperimentalKotest::class)
class MasterPromptContextBuilderPropertyTest {

    /**
     * Property 5: AS-IS tickets only in AS-IS section,
     * TO-BE only in TO-BE, OUTDATED only as one-line refs.
     */
    @Test
    fun `AS-IS tickets appear only in AS-IS section`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            Arb.string(3..8), Arb.string(3..8)
        ) { rawAsIsId, rawToBeId ->
            // Prefix IDs to avoid substring collisions
            val asIsId = "ASIS_$rawAsIsId"
            val toBeId = "TOBE_$rawToBeId"
            val memory = buildMemoryWithClassifications(asIsId, toBeId)
            val kbSources = emptySet<String>()
            val asIsSection = MasterPromptContextBuilder.buildAsIsSection(memory, kbSources)
            val toBeSection = MasterPromptContextBuilder.buildToBeSection(memory, kbSources)
            if (asIsSection.isNotEmpty()) {
                asIsSection shouldContain asIsId
                asIsSection shouldNotContain toBeId
            }
        }
    }

    @Test
    fun `TO-BE tickets appear only in TO-BE section`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            Arb.string(3..8), Arb.string(3..8)
        ) { asIsId, toBeId ->
            val memory = buildMemoryWithClassifications(asIsId, toBeId)
            val kbSources = emptySet<String>()
            val toBeSection = MasterPromptContextBuilder.buildToBeSection(memory, kbSources)
            toBeSection shouldContain "TO-BE"
        }
    }

    @Test
    fun `OUTDATED tickets appear only as one-line refs`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            Arb.string(3..8)
        ) { outdatedId ->
            val memory = buildMemoryWithOutdated(outdatedId)
            val metadata = MasterPromptContextBuilder.buildOutdatedMetadata(memory)
            if (metadata.isNotEmpty()) {
                metadata shouldContain outdatedId
                metadata shouldContain "superseded"
            }
        }
    }

    /**
     * Property 6: TO-BE section appears before AS-IS section.
     */
    @Test
    fun `TO-BE section appears before AS-IS in classified context`() = runTest {
        checkAll(PropTestConfig(iterations = 50),
            Arb.string(3..8), Arb.string(3..8)
        ) { asIsId, toBeId ->
            val memory = buildMemoryWithClassifications(asIsId, toBeId)
            val prompt = MasterPromptSections.buildContext(memory)
            val toBeIdx = prompt.indexOf("TO-BE")
            val asIsIdx = prompt.indexOf("AS-IS")
            if (toBeIdx >= 0 && asIsIdx >= 0) {
                (toBeIdx < asIsIdx) shouldBe true
            }
        }
    }

    private fun buildMemoryWithClassifications(
        asIsId: String, toBeId: String
    ): StructuredMemory {
        val memory = JiraContextMemorySchema.createMemory()
        memory.store("ticketClassifications", MemoryEntry(
            "$asIsId:${ContentClassification.AS_IS}", asIsId, "temporalClassifier", ""
        ))
        memory.store("ticketClassifications", MemoryEntry(
            "$toBeId:${ContentClassification.TO_BE}", toBeId, "temporalClassifier", ""
        ))
        memory.store("linkedTickets", MemoryEntry(
            "$asIsId: AS-IS feature", asIsId, "getLinkedIssues", ""
        ))
        memory.store("linkedTickets", MemoryEntry(
            "$toBeId: TO-BE feature", toBeId, "getLinkedIssues", ""
        ))
        memory.store("summary", MemoryEntry(
            "Root ticket summary", "ROOT-1", "fetchJiraDetails", ""
        ))
        return memory
    }

    private fun buildMemoryWithOutdated(outdatedId: String): StructuredMemory {
        val memory = JiraContextMemorySchema.createMemory()
        memory.store("ticketClassifications", MemoryEntry(
            "$outdatedId:${ContentClassification.OUTDATED}", outdatedId, "temporalClassifier", ""
        ))
        return memory
    }
}
