package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers all dashboard-related route groups:
 * scan routes, estimation routes, and graph routes.
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureDashboardRoutes() {
    scanRoutes()
    estimationRoutes()
    graphRoutes()
}
