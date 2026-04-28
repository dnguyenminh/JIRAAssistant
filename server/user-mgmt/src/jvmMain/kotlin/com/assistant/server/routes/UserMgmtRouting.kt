package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers all user-management-related route groups.
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureUserMgmtRoutes() {
    userRoutes()
}
