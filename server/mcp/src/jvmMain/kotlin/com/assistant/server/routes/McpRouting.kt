package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers all MCP-related route groups: integration, MCP CRUD,
 * MCP health, and MCP runtime routes.
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureMcpRoutes() {
    integrationRoutes()
    mcpRoutes()
    mcpHealthRoutes()
    mcpRuntimeRoutes()
}
