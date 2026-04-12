package com.assistant.frontend.pages.graph

import kotlinx.browser.document

/**
 * Binds click handlers for graph navigation buttons
 * (zoom in, zoom out, fit-to-screen, reset view).
 * Uses Cytoscape.js API.
 *
 * Requirements: 2.1, 2.2
 */
internal object GraphNavControls {

    /**
     * Binds navigation button click handlers to the given Cytoscape instance.
     * @param cy Cytoscape instance (dynamic to avoid tight coupling)
     */
    fun bind(cy: dynamic) {
        bindZoomIn(cy)
        bindZoomOut(cy)
        bindFitToScreen(cy)
        bindResetView(cy)
    }

    private fun bindZoomIn(cy: dynamic) {
        document.getElementById("btnGraphZoomIn")?.addEventListener("click", {
            val level = (cy.zoom() as Double) * 1.3
            val opts = js("({})")
            opts.level = level
            opts.renderedPosition = viewportCenter(cy)
            cy.zoom(opts)
        })
    }

    private fun bindZoomOut(cy: dynamic) {
        document.getElementById("btnGraphZoomOut")?.addEventListener("click", {
            val level = (cy.zoom() as Double) / 1.3
            val opts = js("({})")
            opts.level = level
            opts.renderedPosition = viewportCenter(cy)
            cy.zoom(opts)
        })
    }

    private fun bindFitToScreen(cy: dynamic) {
        document.getElementById("btnGraphFit")?.addEventListener("click", {
            cy.fit(cy.nodes(":visible"), 40)
        })
    }

    private fun bindResetView(cy: dynamic) {
        document.getElementById("btnGraphReset")?.addEventListener("click", {
            cy.fit()
        })
    }

    private fun viewportCenter(cy: dynamic): dynamic {
        val center = js("({})")
        center.x = (cy.width() as Double) / 2.0
        center.y = (cy.height() as Double) / 2.0
        return center
    }
}
