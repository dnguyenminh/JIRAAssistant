package com.assistant.server.routes

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Placeholder test for the knowledge-graph sub-module routing.
 *
 * Currently the KG module has no routes — this test verifies
 * the extension function exists and is callable.
 */
class KnowledgeGraphRoutingTest {

    @Test
    fun `configureKnowledgeGraphRoutes extension exists`() {
        val method = Class.forName(
            "com.assistant.server.routes.KnowledgeGraphRoutingKt"
        ).methods.find { it.name == "configureKnowledgeGraphRoutes" }
        assertNotNull(method, "configureKnowledgeGraphRoutes must exist")
    }
}
