package com.assistant.server.routes

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Placeholder test for the dashboard sub-module routing.
 *
 * Dashboard routes (scan, estimation, graph) require full
 * Koin DI context with BatchScanEngine, ScrumEstimator,
 * GraphEngine, etc. Integration tests live in the aggregator.
 */
class DashboardRoutingTest {

    @Test
    fun `configureDashboardRoutes extension exists`() {
        // Verify the extension function is accessible
        val method = Class.forName(
            "com.assistant.server.routes.DashboardRoutingKt"
        ).methods.find { it.name == "configureDashboardRoutes" }
        assertNotNull(method, "configureDashboardRoutes must exist")
    }
}
