@file:Suppress("UNUSED")

package com.assistant.frontend.pages.graph

/**
 * Kotlin/JS external declaration for Cytoscape.js.
 * Uses require() directly since @JsModule has issues with default exports.
 */
private val cytoscapeModule: dynamic = js("require('cytoscape')")

fun cytoscape(options: dynamic): dynamic = cytoscapeModule(options)
