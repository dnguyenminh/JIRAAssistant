package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Registers all core route groups: health, auth, settings, and project routes.
 *
 * Called by the aggregator's `configureRouting()` function.
 */
fun Routing.configureCoreRoutes() {
    healthRoutes()
    authRoutes()
    settingsRoutes()
    projectRoutes()
}
