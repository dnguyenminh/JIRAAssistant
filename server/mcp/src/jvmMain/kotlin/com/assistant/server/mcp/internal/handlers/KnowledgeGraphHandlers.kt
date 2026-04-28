package com.assistant.server.mcp.internal.handlers

import com.assistant.kb.KBRepository
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * Knowledge Graph tool handlers — get_graph_data, search_graph_nodes.
 * Requirements: AC 6.99–6.100
 */
class KnowledgeGraphHandlers(private val kbRepository: KBRepository) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleGetGraphData(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val graph = kbRepository.getGraphData(projectKey)
            ?: return errorResponse("No graph data found for project: $projectKey")
        return textResponse(json.encodeToString(graph))
    }

    suspend fun handleSearchGraphNodes(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val query = args.str("query") ?: return missingField("query")
        val graph = kbRepository.getGraphData(projectKey)
            ?: return errorResponse("No graph data for project: $projectKey")
        val matched = graph.nodes.filter { node ->
            matchesQuery(node.key, node.summary, node.featureName, query)
        }
        val result = buildJsonObject {
            put("projectKey", projectKey)
            put("query", query)
            put("results", json.encodeToJsonElement(matched))
            put("count", matched.size)
        }
        return textResponse(result.toString())
    }

    private fun matchesQuery(
        key: String, summary: String, feature: String?, query: String
    ): Boolean {
        val q = query.lowercase()
        return key.lowercase().contains(q) ||
            summary.lowercase().contains(q) ||
            (feature?.lowercase()?.contains(q) == true)
    }
}
