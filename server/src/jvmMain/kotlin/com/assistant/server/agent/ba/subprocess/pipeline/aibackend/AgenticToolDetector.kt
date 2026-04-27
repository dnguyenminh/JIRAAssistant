package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor

/**
 * Result of dynamic tool detection from available ToolDescriptors.
 * All string fields contain actual tool names from the descriptor list.
 */
internal data class DetectedTools(
    val getIssueTool: String?,
    val searchTool: String?,
    val analyzeTool: String?,
    val getAnalysisTool: String?,
    val kbSearchTool: String?,
    val kbReadTool: String?,
    val kbContextTool: String?,
    val kbIngestTool: String?,
    val diagramTool: String?,
    val dbQueryTool: String?,
    val docConvertTool: String?,
    val hasKbTools: Boolean,
    val hasJiraTools: Boolean,
    val hasAnalysisTools: Boolean,
    val hasDiagramTools: Boolean,
    val hasDbTools: Boolean,
    val hasDocConvertTools: Boolean
)

/**
 * Detect tool categories from available ToolDescriptors.
 * Uses case-insensitive pattern matching on tool names.
 * Deterministic: sorts by name first so input order is irrelevant.
 */
internal fun detectToolCategories(
    tools: List<ToolDescriptor>
): DetectedTools {
    val sorted = tools.sortedBy { it.name.lowercase() }
    val jira = detectJiraTools(sorted)
    val kb = detectKbTools(sorted)
    val extra = detectExtraTools(sorted)
    return DetectedTools(
        getIssueTool = jira.first,
        searchTool = jira.second,
        analyzeTool = jira.third,
        getAnalysisTool = jira.fourth,
        kbSearchTool = kb.first,
        kbReadTool = kb.second,
        kbContextTool = kb.third,
        kbIngestTool = kb.fourth,
        diagramTool = extra.first,
        dbQueryTool = extra.second,
        docConvertTool = extra.third,
        hasKbTools = listOfNotNull(kb.first, kb.second, kb.third, kb.fourth).isNotEmpty(),
        hasJiraTools = listOfNotNull(jira.first, jira.second).isNotEmpty(),
        hasAnalysisTools = jira.third != null || jira.fourth != null,
        hasDiagramTools = extra.first != null,
        hasDbTools = extra.second != null,
        hasDocConvertTools = extra.third != null
    )
}

private data class Quad(
    val first: String?, val second: String?,
    val third: String?, val fourth: String?
)

private fun detectJiraTools(sorted: List<ToolDescriptor>): Quad {
    val n = { td: ToolDescriptor -> td.name.lowercase() }
    return Quad(
        first = sorted.firstOrNull { n(it).contains("get_issue") }?.name,
        second = sorted.firstOrNull { n(it).contains("search") && n(it).contains("jira") }?.name,
        third = sorted.firstOrNull { n(it).contains("analyze_ticket") }?.name,
        fourth = sorted.firstOrNull { n(it).contains("get_ticket_analysis") }?.name
    )
}

private fun detectKbTools(sorted: List<ToolDescriptor>): Quad {
    val n = { td: ToolDescriptor -> td.name.lowercase() }
    val search = sorted.firstOrNull { n(it).contains("kb_search_smart") }?.name
        ?: sorted.firstOrNull { n(it).contains("kb_search") }?.name
    val ingest = sorted.firstOrNull { n(it).contains("kb_ingest") }?.name
        ?: sorted.firstOrNull { n(it).contains("kb_write") }?.name
    return Quad(
        first = search,
        second = sorted.firstOrNull { n(it).contains("kb_read") }?.name,
        third = sorted.firstOrNull { n(it).contains("kb_context") }?.name,
        fourth = ingest
    )
}


private data class ExtraTriple(
    val first: String?, val second: String?, val third: String?
)

private fun detectExtraTools(sorted: List<ToolDescriptor>): ExtraTriple {
    val n = { td: ToolDescriptor -> td.name.lowercase() }
    val diagram = sorted.firstOrNull {
        val name = n(it)
        name.contains("drawio") || name.contains("draw.io") || name.contains("diagram")
    }?.name
    val db = sorted.firstOrNull {
        val name = n(it)
        name.contains("execute_sql") || name.contains("query") && name.contains("db")
    }?.name
    val doc = sorted.firstOrNull {
        val name = n(it)
        name.contains("convert_to_markdown") || name.contains("markitdown")
    }?.name
    return ExtraTriple(first = diagram, second = db, third = doc)
}
