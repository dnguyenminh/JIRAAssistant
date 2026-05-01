package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers knowledge-graph-specific route groups.
 *
 * Currently empty — graph visualization routes are in
 * the :server:dashboard module (GraphRoutes.kt).
 * This extension point exists for future KG-specific
 * endpoints (e.g., graph CRUD, ontology management).
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureKnowledgeGraphRoutes() {
    // No KG-specific routes yet; graph visualization
    // is handled by :server:dashboard
}
