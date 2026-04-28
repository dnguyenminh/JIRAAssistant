package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers all document-generation-related route groups:
 * document routes, job routes, and collection job routes.
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureDocgenRoutes() {
    documentRoutes()
    jobRoutes()
    collectionJobRoutes()
}
