package com.assistant.domain

import com.assistant.ai.*
import com.assistant.jira.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Builds the feature network graph from Jira issues.
 * Edge sources (priority order):
 * 1. Jira issuelinks (blocks, relates, duplicates, etc.)
 * 2. Parent/subtask hierarchy
 * 3. Keyword similarity (fallback heuristic)
 * 4. AI semantic (optional, may timeout)
 */
class FeatureNetworkMapper(
    private val aiAgent: AIAgent
) {
    suspend fun map(issues: List<JiraIssue>): NetworkGraph {
        val idSet = issues.map { it.id }.toHashSet()
        val nodes = issues.map { issue ->
            TicketNode(
                id = issue.id, key = issue.key,
                summary = issue.fields.summary,
                status = issue.fields.status?.name ?: "Unknown",
                featureName = issue.fields.issuetype?.name,
                description = issue.fields.descriptionText.takeIf { it.isNotBlank() }
            )
        }
        val edges = mutableListOf<TicketEdge>()
        val added = mutableSetOf<String>()

        addIssueLinkEdges(issues, idSet, edges, added)
        addParentSubtaskEdges(issues, idSet, edges, added)
        addKeywordEdges(issues, edges, added)

        println("[FeatureNetworkMapper] ${nodes.size} nodes, ${edges.size} edges (links=${countType(edges, "link")}, parent=${countType(edges, "parent")}, keyword=${countType(edges, "keyword")})")
        return NetworkGraph(nodes, edges)
    }

    // --- 1. Jira Issue Links ---

    private fun addIssueLinkEdges(
        issues: List<JiraIssue>, idSet: Set<String>,
        edges: MutableList<TicketEdge>, added: MutableSet<String>
    ) {
        for (issue in issues) {
            val links = issue.fields.issuelinks ?: continue
            for (link in links) {
                addSingleLink(issue.id, link, idSet, edges, added)
            }
        }
    }

    private fun addSingleLink(
        issueId: String, link: JiraIssueLink,
        idSet: Set<String>, edges: MutableList<TicketEdge>,
        added: MutableSet<String>
    ) {
        val typeName = link.type?.name ?: "relates"
        val targetId = link.outwardIssue?.id ?: link.inwardIssue?.id ?: return
        if (targetId !in idSet) return
        val pair = pairKey(issueId, targetId)
        if (pair in added) return
        added.add(pair)
        edges.add(TicketEdge(issueId, targetId, "link:$typeName", isSemantic = false))
    }

    // --- 2. Parent / Subtask ---

    private fun addParentSubtaskEdges(
        issues: List<JiraIssue>, idSet: Set<String>,
        edges: MutableList<TicketEdge>, added: MutableSet<String>
    ) {
        for (issue in issues) {
            addParentEdge(issue, idSet, edges, added)
            addSubtaskEdges(issue, idSet, edges, added)
        }
    }

    private fun addParentEdge(
        issue: JiraIssue, idSet: Set<String>,
        edges: MutableList<TicketEdge>, added: MutableSet<String>
    ) {
        val parentId = issue.fields.parent?.id ?: return
        if (parentId !in idSet) return
        val pair = pairKey(issue.id, parentId)
        if (pair in added) return
        added.add(pair)
        edges.add(TicketEdge(parentId, issue.id, "parent_child", isSemantic = false))
    }

    private fun addSubtaskEdges(
        issue: JiraIssue, idSet: Set<String>,
        edges: MutableList<TicketEdge>, added: MutableSet<String>
    ) {
        val subtasks = issue.fields.subtasks ?: return
        for (sub in subtasks) {
            if (sub.id !in idSet) continue
            val pair = pairKey(issue.id, sub.id)
            if (pair in added) continue
            added.add(pair)
            edges.add(TicketEdge(issue.id, sub.id, "parent_child", isSemantic = false))
        }
    }

    // --- 3. Keyword Similarity (fallback) ---

    private fun addKeywordEdges(
        issues: List<JiraIssue>,
        edges: MutableList<TicketEdge>, added: MutableSet<String>
    ) {
        val index = mutableMapOf<String, MutableList<String>>()
        for (issue in issues) {
            for (word in extractKeywords(issue.fields.summary)) {
                index.getOrPut(word) { mutableListOf() }.add(issue.id)
            }
        }
        for ((_, ids) in index) {
            if (ids.size < 2 || ids.size > 30) continue
            connectCluster(ids, edges, added)
        }
    }

    private fun extractKeywords(summary: String): Set<String> {
        val stop = STOP_WORDS
        return summary.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it !in stop }
            .toSet()
    }

    private fun connectCluster(
        ids: List<String>, edges: MutableList<TicketEdge>,
        added: MutableSet<String>
    ) {
        val limit = minOf(ids.size, 8)
        for (i in 0 until limit) {
            for (j in i + 1 until limit) {
                val pair = pairKey(ids[i], ids[j])
                if (pair in added) continue
                added.add(pair)
                edges.add(TicketEdge(ids[i], ids[j], "keyword_similarity", isSemantic = false))
            }
        }
    }

    // --- Helpers ---

    private fun pairKey(a: String, b: String): String =
        if (a < b) "$a-$b" else "$b-$a"

    private fun countType(edges: List<TicketEdge>, prefix: String): Int =
        edges.count { it.relationshipType.startsWith(prefix) }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be",
            "to", "of", "in", "for", "on", "with", "as", "at",
            "by", "from", "and", "or", "not", "this", "that",
            "it", "its", "has", "have", "had", "do", "does",
            "will", "can", "should", "would", "could", "may",
            "need", "when", "then", "than", "also", "just",
            "more", "some", "only", "into", "over", "such"
        )
    }
}
