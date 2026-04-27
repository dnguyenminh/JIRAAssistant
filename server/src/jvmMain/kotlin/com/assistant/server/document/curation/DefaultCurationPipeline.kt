package com.assistant.server.document.curation

import com.assistant.kb.KBRecord
import com.assistant.server.document.curation.models.*
import com.assistant.server.document.models.EnrichedContext
import org.slf4j.LoggerFactory

/**
 * Default implementation of [CurationPipeline].
 *
 * Orchestrates: TemporalClassifier → CommentSummarizer →
 * AttachmentCurator → BudgetEnforcer.
 *
 * Stateless and deterministic.
 *
 * Requirements: 1.1–1.5, 3.1–3.6, 8.1, 8.5, 9.2, 9.3
 */
class DefaultCurationPipeline(
    private val temporalClassifier: TemporalClassifier,
    private val commentSummarizer: CommentSummarizer,
    private val attachmentCurator: AttachmentCurator,
    private val budgetEnforcer: BudgetEnforcer
) : CurationPipeline {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun curate(context: EnrichedContext): CuratedContext {
        val startTime = System.currentTimeMillis()
        val originalSize = estimateContextSize(context)

        val classifications = classifyLinkedTickets(context)
        val sections = buildSections(context, classifications)
        val comments = summarizeComments(context)
        val attachments = curateAttachments(context)
        val referenceOnly = buildReferenceOnlyList(context, classifications)

        val curatedContext = CuratedContext(
            rootTicket = context.mainTicket,
            toBeSection = sections.first,
            asIsSection = sections.second,
            outdatedMetadata = sections.third,
            commentSummaries = comments,
            attachments = attachments,
            referenceOnlyTickets = referenceOnly,
            metrics = buildMetrics(
                originalSize, classifications, comments, attachments, startTime
            )
        )

        val result = budgetEnforcer.enforce(
            curatedContext, CurationConfig.MAX_PROMPT_CHARS
        )
        logCurationResult(result)
        return result.context
    }

    private fun classifyLinkedTickets(
        context: EnrichedContext
    ): List<TicketClassification> {
        val rootTicket = context.allTickets.firstOrNull() ?: return emptyList()
        val kbMap = context.linkedTicketAnalyses.associateBy { it.ticketId }
        return context.allTickets.drop(1).map { linked ->
            temporalClassifier.classify(rootTicket, linked, kbMap[linked.summary])
        }
    }

    private fun buildSections(
        context: EnrichedContext,
        classifications: List<TicketClassification>
    ): Triple<ToBeSection, AsIsSection, List<OutdatedReference>> {
        val kbMap = context.linkedTicketAnalyses.associateBy { it.ticketId }
        val toBeLinked = mutableListOf<ClassifiedTicketData>()
        val asIsItems = mutableListOf<ClassifiedTicketData>()
        val outdated = mutableListOf<OutdatedReference>()

        for (classification in classifications) {
            when (classification.contentClassification) {
                ContentClassification.TO_BE -> {
                    toBeLinked.add(buildTicketData(classification, kbMap))
                }
                ContentClassification.AS_IS -> {
                    asIsItems.add(buildTicketData(classification, kbMap))
                }
                ContentClassification.OUTDATED -> {
                    outdated.add(buildOutdatedRef(classification, kbMap))
                }
            }
        }

        val rootReqs = context.mainTicket.extractedRequirements
        return Triple(
            ToBeSection(rootReqs, toBeLinked),
            AsIsSection(asIsItems),
            outdated
        )
    }

    private fun buildTicketData(
        classification: TicketClassification,
        kbMap: Map<String, KBRecord>
    ): ClassifiedTicketData {
        val kb = kbMap[classification.ticketId]
        return ClassifiedTicketData(
            ticketId = classification.ticketId,
            classification = classification.contentClassification,
            businessSummary = kb?.businessSummary ?: "",
            asIsState = kb?.asIsState ?: "",
            toBeState = kb?.toBeState ?: "",
            extractedRequirements = kb?.extractedRequirements ?: emptyList()
        )
    }

    private fun buildOutdatedRef(
        classification: TicketClassification,
        kbMap: Map<String, KBRecord>
    ): OutdatedReference {
        val kb = kbMap[classification.ticketId]
        return OutdatedReference(
            ticketId = classification.ticketId,
            supersededBy = classification.supersededBy ?: "",
            oneLinerSummary = kb?.requirementSummary ?: "No summary"
        )
    }

    private fun summarizeComments(
        context: EnrichedContext
    ): Map<String, CommentSummary> {
        val kbTicketIds = context.linkedTicketAnalyses.map { it.ticketId }.toSet()
        return context.rawComments.mapValues { (ticketId, comments) ->
            commentSummarizer.summarize(comments, ticketId in kbTicketIds)
        }
    }

    private fun curateAttachments(
        context: EnrichedContext
    ): List<CuratedAttachment> {
        val rootId = context.mainTicket.ticketId
        val kbFilenames = extractKbReferencedFilenames(context)
        val rootAtts = context.allAttachmentChunks.filter {
            it.filename.contains(rootId)
        }
        val linkedAtts = context.allAttachmentChunks.filter {
            !it.filename.contains(rootId)
        }
        return attachmentCurator.curate(rootAtts, linkedAtts, kbFilenames)
    }

    private fun extractKbReferencedFilenames(
        context: EnrichedContext
    ): Set<String> {
        return context.linkedTicketAnalyses
            .flatMap { it.technicalDetails.toString().split(",") }
            .filter { it.contains(".") }
            .map { it.trim() }
            .toSet()
    }

    private fun buildReferenceOnlyList(
        context: EnrichedContext,
        classifications: List<TicketClassification>
    ): List<TicketReference> {
        val kbMap = context.linkedTicketAnalyses.associateBy { it.ticketId }
        return classifications
            .filter { kbMap.containsKey(it.ticketId) }
            .take(CurationConfig.MAX_MCP_LOOKUPS)
            .map { c ->
                TicketReference(
                    ticketId = c.ticketId,
                    oneLinerSummary = kbMap[c.ticketId]?.requirementSummary ?: ""
                )
            }
    }

    private fun buildMetrics(
        originalSize: Int,
        classifications: List<TicketClassification>,
        comments: Map<String, CommentSummary>,
        attachments: List<CuratedAttachment>,
        startTime: Long
    ): CurationMetrics {
        return CurationMetrics(
            originalContextSizeChars = originalSize,
            curatedContextSizeChars = 0, // updated after budget enforcement
            ticketsAsIs = classifications.count {
                it.contentClassification == ContentClassification.AS_IS
            },
            ticketsToBe = classifications.count {
                it.contentClassification == ContentClassification.TO_BE
            },
            ticketsOutdated = classifications.count {
                it.contentClassification == ContentClassification.OUTDATED
            },
            ticketsReferenceOnly = classifications.size,
            commentsSummarized = comments.size,
            attachmentsCurated = attachments.size,
            curationTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun estimateContextSize(context: EnrichedContext): Int {
        return context.toString().length
    }

    private fun logCurationResult(result: BudgetResult) {
        logger.info(
            "Curation complete: original={}chars, final={}chars, truncated={}",
            result.originalSize, result.finalSize, result.truncationApplied
        )
        if (result.finalSize > CurationConfig.MAX_PROMPT_CHARS) {
            logger.warn(
                "Post-curation prompt exceeds budget: {} > {}",
                result.finalSize, CurationConfig.MAX_PROMPT_CHARS
            )
        }
    }
}
