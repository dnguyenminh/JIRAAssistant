package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers all analysis-related route groups: analysis, attachment,
 * ticket detail, and cascade routes.
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureAnalysisRoutes() {
    analysisRoutes()
    attachmentRoutes()
    ticketDetailRoutes()
    cascadeRoutes()
}
