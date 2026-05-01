package com.assistant.server.routes

import io.ktor.server.routing.*

/**
 * Aggregated chat route registration for the server/chat sub-module.
 * Called by the aggregator's configureRouting().
 */
fun Routing.configureChatRoutes() {
    chatRoutes()
    chatUploadRoutes()
}
