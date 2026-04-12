package com.assistant.server.chat

import com.assistant.server.attachment.models.ChunkType
import com.assistant.server.indexing.EmbedItem
import com.assistant.server.indexing.IndexingPipeline
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles Confluence MCP tool results: extracts page data,
 * indexes summaries into VectorStore, builds openUrl actions.
 * Requirements: 19.1, 19.2, 19.3, 19.4
 */
class ConfluenceMcpSyncHandler(
    private val indexingPipeline: IndexingPipeline?
) {
    private val logger = LoggerFactory.getLogger(ConfluenceMcpSyncHandler::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val CONFLUENCE_TOOLS = setOf(
            "search_confluence", "confluence_search",
            "get_page", "get_confluence_page",
            "search_content", "confluence_search_content"
        )

        fun formatConfluenceText(page: ConfluencePage): String {
            val base = "[Confluence] ${page.title}"
            return if (page.summary.isNotBlank()) "$base. ${page.summary}" else base
        }
    }

    /** Check if a tool name is a Confluence tool we should handle. */
    fun isConfluenceTool(toolName: String): Boolean =
        CONFLUENCE_TOOLS.contains(toolName) ||
            toolName.contains("confluence", ignoreCase = true)

    /**
     * Process Confluence tool result: extract pages, index summaries.
     * Returns list of ConfluencePage for building openUrl actions.
     * Req: 19.3, 19.4
     */
    suspend fun processToolResult(
        projectKey: String, toolResult: String
    ): List<ConfluencePage> {
        val pages = extractPages(toolResult)
        if (pages.isEmpty()) return emptyList()
        indexPages(projectKey, pages)
        return pages
    }

    internal fun extractPages(toolResult: String): List<ConfluencePage> {
        return try {
            val element = parseJsonElement(toolResult) ?: return emptyList()
            extractPagesFromElement(element)
        } catch (e: Exception) {
            logger.debug("Could not parse Confluence result: ${e.message}")
            emptyList()
        }
    }

    private fun extractPagesFromElement(el: JsonElement): List<ConfluencePage> =
        when (el) {
            is JsonArray -> el.mapNotNull { extractSinglePage(it) }
            is JsonObject -> extractFromObject(el)
            else -> emptyList()
        }

    private fun extractFromObject(obj: JsonObject): List<ConfluencePage> {
        val single = extractSinglePage(obj)
        if (single != null) return listOf(single)
        val results = obj["results"]?.jsonArray
            ?: obj["pages"]?.jsonArray
            ?: obj["content"]?.jsonArray
        return results?.mapNotNull { extractSinglePage(it) } ?: emptyList()
    }

    internal fun extractSinglePage(element: JsonElement): ConfluencePage? {
        val obj = element as? JsonObject ?: return null
        val title = extractStr(obj, "title") ?: return null
        val url = extractStr(obj, "url")
            ?: extractStr(obj, "_links", "webui")
            ?: extractStr(obj, "webUrl")
        val summary = extractStr(obj, "excerpt")
            ?: extractStr(obj, "summary")
            ?: extractStr(obj, "description") ?: ""
        val id = extractStr(obj, "id") ?: title.hashCode().toString()
        return ConfluencePage(id = id, title = title, url = url, summary = summary)
    }

    private suspend fun indexPages(projectKey: String, pages: List<ConfluencePage>) {
        if (indexingPipeline == null) {
            logger.warn("IndexingPipeline unavailable, skipping Confluence indexing")
            return
        }
        val items = pages.map { buildEmbedItem(it) }
        indexingPipeline.indexConfluencePages(projectKey, items)
    }

    private fun buildEmbedItem(page: ConfluencePage) = EmbedItem(
        text = formatConfluenceText(page),
        ticketId = "confluence-${page.id}",
        attachmentId = "confluence:${page.id}",
        chunkType = ChunkType.CONFLUENCE,
        filename = page.title
    )

    private fun parseJsonElement(text: String): JsonElement? = try {
        val trimmed = text.trim()
        val start = trimmed.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) null
        else json.parseToJsonElement(trimmed.substring(start))
    } catch (_: Exception) { null }

    private fun extractStr(obj: JsonObject, vararg path: String): String? {
        if (path.isEmpty()) return null
        var current: JsonElement = obj
        for (key in path) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return (current as? JsonPrimitive)?.contentOrNull
    }
}

/** Represents a Confluence page extracted from MCP tool result. */
data class ConfluencePage(
    val id: String,
    val title: String,
    val url: String?,
    val summary: String
)
