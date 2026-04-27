package com.assistant.server.document.curation.generators

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.kb.KBRecord
import com.assistant.server.document.models.EnrichedContext
import com.assistant.server.document.models.FullComment
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

/**
 * Shared Arb generators for curation pipeline property tests.
 */
object CurationArbitraries {

    fun arbKBRecord(): Arb<KBRecord> = arbitrary {
        KBRecord(
            ticketId = Arb.string(5..10).bind(),
            requirementSummary = Arb.string(10..200).bind(),
            evolutionHistory = emptyList(),
            scrumPoints = Arb.double(0.5..13.0).bind(),
            confidenceScore = Arb.double(0.1..1.0).bind(),
            rationale = Arb.string(10..100).bind(),
            similarTicketRefs = emptyList(),
            timestamp = "2025-01-01T00:00:00Z",
            businessSummary = Arb.string(20..500).bind(),
            asIsState = Arb.string(0..300).bind(),
            toBeState = Arb.string(0..300).bind(),
            extractedRequirements = Arb.list(
                Arb.string(10..100), 0..5
            ).bind()
        )
    }

    fun arbStructuredTicketContent(
        dateRange: Pair<String, String> = "2024-01-01" to "2025-12-31"
    ): Arb<StructuredTicketContent> = arbitrary {
        StructuredTicketContent(
            summary = Arb.string(5..50).bind(),
            description = Arb.string(20..2000).bind(),
            status = Arb.element(
                "Open", "In Progress", "Done", "Closed"
            ).bind(),
            priority = Arb.element("High", "Medium", "Low").bind(),
            issueType = Arb.element("Story", "Bug", "Task").bind(),
            createdDate = arbIsoDate(dateRange).bind(),
            updatedDate = arbIsoDate(dateRange).bind()
        )
    }

    fun arbFullComment(isBot: Boolean = false): Arb<FullComment> = arbitrary {
        val author = if (isBot) {
            Arb.element(
                "ScriptRunner", "Jira Automation", "Bitbucket Bot"
            ).bind()
        } else {
            Arb.string(3..20).bind()
        }
        val body = if (isBot) {
            Arb.element(
                "Status changed from Open to In Progress",
                "Field updated: priority",
                "Automatically generated reminder"
            ).bind()
        } else {
            Arb.string(10..500).bind()
        }
        FullComment(
            author = author,
            createdDate = arbIsoDate().bind(),
            body = body
        )
    }

    fun arbCommentList(size: IntRange = 0..30): Arb<List<FullComment>> =
        arbitrary {
            val count = Arb.int(size).bind()
            val botRatio = Arb.double(0.1..0.5).bind()
            val botCount = (count * botRatio).toInt()
            val substantive = List(count - botCount) {
                arbFullComment(isBot = false).bind()
            }
            val bots = List(botCount) {
                arbFullComment(isBot = true).bind()
            }
            (substantive + bots).sortedBy { it.createdDate }
        }

    fun arbAttachmentChunkInfo(): Arb<AttachmentChunkInfo> = arbitrary {
        val isReqDoc = Arb.boolean().bind()
        val filename = if (isReqDoc) {
            Arb.element(
                "BRD_v2.pdf", "FSD_draft.docx", "requirements.xlsx"
            ).bind()
        } else {
            Arb.element(
                "screenshot.png", "design.fig", "notes.txt", "data.csv"
            ).bind()
        }
        AttachmentChunkInfo(
            filename = filename,
            content = Arb.string(100..10000).bind(),
            similarityScore = Arb.float(0.1f..1.0f).bind()
        )
    }

    fun arbTicketDates(): Arb<Pair<String, String>> = arbitrary {
        val root = arbIsoDate("2024-06-01" to "2024-12-31").bind()
        val linked = arbIsoDate("2024-01-01" to "2025-06-30").bind()
        root to linked
    }

    fun arbEnrichedContext(
        ticketCount: IntRange = 1..10
    ): Arb<EnrichedContext> = arbitrary {
        val mainKb = arbKBRecord().bind()
        val count = Arb.int(ticketCount).bind()
        val tickets = List(count) {
            arbStructuredTicketContent().bind()
        }
        val linkedKbs = List((count - 1).coerceAtLeast(0)) {
            arbKBRecord().bind()
        }
        val comments = tickets.associate {
            it.summary to arbCommentList(0..15).bind()
        }
        val attachments = List(Arb.int(0..5).bind()) {
            arbAttachmentChunkInfo().bind()
        }
        EnrichedContext(
            mainTicket = mainKb,
            linkedTicketAnalyses = linkedKbs,
            allTickets = tickets,
            rawComments = comments,
            allAttachmentChunks = attachments
        )
    }

    private fun arbIsoDate(
        range: Pair<String, String> = "2024-01-01" to "2025-12-31"
    ): Arb<String> = arbitrary {
        val year = Arb.int(2024..2025).bind()
        val month = Arb.int(1..12).bind().toString().padStart(2, '0')
        val day = Arb.int(1..28).bind().toString().padStart(2, '0')
        "$year-$month-${day}T00:00:00Z"
    }
}
