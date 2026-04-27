package com.assistant.frontend.pages.graph

/**
 * Layout helper functions for CytoscapeRenderer.
 * Extracted to keep CytoscapeRenderer under 200 lines.
 */
internal object CytoscapeLayoutHelper {

    /** Run cose layout scoped to new nodes only — preserves existing positions. */
    fun runIncrementalLayout(c: dynamic, newNodeIds: Set<String>) {
        if (newNodeIds.isEmpty()) return
        val selector = newNodeIds.joinToString(", ") { "#$it" }
        val newEles = c.nodes(selector)
        if (newEles.length == 0) return
        val opts = js("({})")
        opts.name = "cose"; opts.animate = true; opts.animationDuration = 300
        opts.fit = false
        opts.nodeRepulsion = js("(function(){return 8000;})")
        opts.idealEdgeLength = js("(function(){return 80;})")
        newEles.layout(opts).run()
    }

    fun layoutVisible(c: dynamic, count: Int) {
        val name = if (count <= 30) "circle" else "cose"
        val opts = js("({})")
        opts.name = name; opts.animate = true
        opts.animationDuration = 300; opts.fit = true; opts.padding = 40
        if (name == "cose") {
            opts.nodeRepulsion = js("(function(){return 8000;})")
            opts.idealEdgeLength = js("(function(){return 80;})")
        }
        c.nodes(":visible").layout(opts).run()
    }

    /** Concentric layout: focused node at center, neighbors in rings by BFS depth. */
    fun layoutConcentric(c: dynamic, focusId: String) {
        val depths = computeDepthMap(c, focusId)
        val opts = js("({})")
        opts.name = "concentric"
        opts.animate = true; opts.animationDuration = 400
        opts.fit = true; opts.padding = 50
        opts.minNodeSpacing = 30
        opts.concentric = { node: dynamic ->
            val id = node.id() as String
            val d = depths[id] ?: 99
            (100 - d).toDouble()
        }
        opts.levelWidth = js("(function(){return 1;})")
        c.nodes(":visible").layout(opts).run()
    }

    /** BFS from focusId on visible nodes, returns nodeId → depth map. */
    private fun computeDepthMap(c: dynamic, focusId: String): Map<String, Int> {
        val map = mutableMapOf(focusId to 0)
        var frontier = listOf(focusId)
        var depth = 0
        while (frontier.isNotEmpty()) {
            depth++
            val next = mutableListOf<String>()
            for (nid in frontier) {
                val node = c.getElementById(nid)
                if (node.length == 0) continue
                node.neighborhood("node:visible").forEach { nb: dynamic ->
                    val nbId = nb.id() as String
                    if (nbId !in map) { map[nbId] = depth; next.add(nbId) }
                }
            }
            frontier = next
        }
        return map
    }
}
