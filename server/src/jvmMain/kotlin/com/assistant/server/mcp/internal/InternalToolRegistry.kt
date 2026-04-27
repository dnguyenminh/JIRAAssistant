package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition

/**
 * Registry of all internal MCP tool definitions.
 * Aggregates tools from category-specific definition objects.
 * Requirements: AC 6.108
 */
class InternalToolRegistry {

    private val tools = mutableMapOf<String, InternalToolDefinition>()

    init {
        registerAllTools()
    }

    /** Get all registered tool definitions. */
    fun getAllTools(): List<InternalToolDefinition> = tools.values.toList()

    /** Get a specific tool definition by name, or null if not found. */
    fun getTool(name: String): InternalToolDefinition? = tools[name]

    /** Register a single tool definition. */
    private fun register(tool: InternalToolDefinition) {
        tools[tool.name] = tool
    }

    /** Register all tool definitions from category objects. */
    private fun registerAllTools() {
        NavigationToolDefs.all().forEach(::register)
        ScanToolDefs.all().forEach(::register)
        AnalysisToolDefs.all().forEach(::register)
        ChatToolDefs.all().forEach(::register)
        SettingsToolDefs.all().forEach(::register)
        IntegrationToolDefs.all().forEach(::register)
    }
}
