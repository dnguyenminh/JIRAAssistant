package com.assistant.server.routes

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Placeholder test for the user-mgmt sub-module routing.
 *
 * User routes require full Koin DI context with UserStore,
 * AuditLogStore, RBACEngine, etc. Integration tests live
 * in the aggregator.
 */
class UserMgmtRoutingTest {

    @Test
    fun `configureUserMgmtRoutes extension exists`() {
        val method = Class.forName(
            "com.assistant.server.routes.UserMgmtRoutingKt"
        ).methods.find { it.name == "configureUserMgmtRoutes" }
        assertNotNull(method, "configureUserMgmtRoutes must exist")
    }
}
