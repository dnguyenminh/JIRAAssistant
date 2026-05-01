package com.assistant.server.document

import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.ai.deepanalysis.models.LinkedTicketContent
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.jira.JiraClient
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.models.TicketNode
import com.assistant.server.document.models.TraversalConfig
import com.assistant.server.document.traversal.KBFirstTicketFetcher
import com.assistant.server.document.traversal.TicketFetcher
import com.assistant.server.document.traversal.TraversalEngine
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory

/**
 * Deep BFS-based implementation of [JiraContentExtractor].
 *
 * Uses [TraversalEngine] for recursive BFS traversal instead of the
 * shallow 5-ticket fetch in [JiraContentExtractorImpl]. Converts
 * [TicketGraph] nodes into [LinkedTicketContent] for the AI prompt.
 *
 * Uses smaller traversal limits (maxDepth=3, maxTickets=20) compared
 * to document generation (maxDepth=5, maxTickets=50) for faster analysis.
 */
class DeepJiraContentExtractor(
    private val jiraClientProvider: () -> JiraClient,
    private val sectionClassifier: SectionClassifier,
    private val traversalConfigProvider: () -> TraversalConfig,
    private val jiraApiSemaphore: Semaphore,
    private val ticketGraphHolder: TicketGraphHolder? = null,
    private val kbRepository: com.assistant.kb.KBRepository? = null
) : JiraContentExtractor {

    private val logger = LoggerFactory.getLogger(DeepJiraContentExtractor::class.java)

    override suspend fun extract(ticketId: String): StructuredTicketContent {
        val config = traversalConfigProvider().validated()
        val graph = traverseTicketGraph(ticketId, config)
        ticketGraphHolder?.store(ticketId, graph)
        val rootContent = extractRootContent(graph, ticketId)
        val linkedContents = convertNodesToLinkedContents(graph, ticketId)
        logTraversalResults(ticketId, graph, linkedContents)
        return rootContent.copy(linkedTicketContents = linkedContents)
    }

    /** Run BFS traversal from root ticket using TraversalEngine. */
    private suspend fun traverseTicketGraph(
        ticketId: String,
        config: TraversalConfig
    ): TicketGraph {
        val client = jiraClientProvider()
        logger.info(
            "Deep extraction starting for {} with client={}, config: maxDepth={}, maxTickets={}",
            ticketId, client::class.simpleName, config.maxDepth, config.maxTickets
        )
        val fetcher = if (kbRepository != null) {
            KBFirstTicketFetcher(client, sectionClassifier, kbRepository)
        } else {
            TicketFetcher(client, sectionClassifier)
        }
        val engine = TraversalEngine(fetcher, config, jiraApiSemaphore)
        return engine.traverse(ticketId)
    }

    /** Extract root ticket's StructuredTicketContent from the graph. */
    private fun extractRootContent(
        graph: TicketGraph,
        ticketId: String
    ): StructuredTicketContent {
        val rootNode = graph.nodes[ticketId]
            ?: throw IllegalStateException("Root ticket $ticketId not found in graph")
        return rootNode.issue
    }

    /** Convert non-root graph nodes to LinkedTicketContent list. */
    private fun convertNodesToLinkedContents(
        graph: TicketGraph,
        rootTicketId: String
    ): List<LinkedTicketContent> {
        return graph.nodes.values
            .filter { it.ticketId != rootTicketId }
            .sortedByDescending { it.relevanceScore }
            .map { nodeToLinkedContent(it) }
    }

    /** Map a single TicketNode to LinkedTicketContent. */
    private fun nodeToLinkedContent(node: TicketNode): LinkedTicketContent {
        return LinkedTicketContent(
            ticketId = node.ticketId,
            summary = node.issue.summary,
            description = node.issue.description,
            status = node.issue.status,
            linkType = node.discoveredVia.toLinkTypeLabel(),
            comments = node.issue.comments,
            attachments = node.issue.attachments
        )
    }

    /** Log traversal results at INFO level. */
    private fun logTraversalResults(
        ticketId: String,
        graph: TicketGraph,
        linkedContents: List<LinkedTicketContent>
    ) {
        logger.info(
            "Deep extraction for {}: {} total tickets, {} linked contents, " +
                "max_depth={}, elapsed={}ms",
            ticketId,
            graph.metadata.totalFetched,
            linkedContents.size,
            graph.metadata.maxDepthReached,
            graph.metadata.traversalTimeMs
        )
    }

    companion object {
        /**
         * Analysis config for map-reduce mode: maxDepth=20, maxTickets=1000.
         * BFS traverses all reachable tickets until timeout (10 minutes).
         * Early termination is disabled — data will be split into batches
         * by MapReduceOrchestrator, so no need to fit into a single prompt.
         */
        fun analysisConfig(): TraversalConfig = TraversalConfig(
            maxDepth = 20,
            maxTickets = 1000,
            totalTimeoutMs = 600_000,
            disableEarlyTermination = true
        )
    }
}

/** Convert RelationshipType to human-readable link type label. */
private fun RelationshipType.toLinkTypeLabel(): String = when (this) {
    RelationshipType.ROOT -> "root"
    RelationshipType.ISSUE_LINK -> "issue-link"
    RelationshipType.SUB_TASK -> "sub-task"
    RelationshipType.PARENT -> "parent"
    RelationshipType.TEXT_REFERENCE -> "text-reference"
}
