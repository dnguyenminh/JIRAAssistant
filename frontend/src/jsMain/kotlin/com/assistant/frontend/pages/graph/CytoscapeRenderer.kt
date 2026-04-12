package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphNode
import com.assistant.frontend.models.GraphEdge
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Cytoscape.js renderer for the Knowledge Graph page.
 * Native drag, hide/show, auto-layout, camera fit — no hacks needed.
 *
 * Requirements: 1.1, 1.3, 1.4, 1.5, 5.1, 5.2, 5.5, 7.1, 7.2, 8.1
 */
internal object CytoscapeRenderer {

    private var cy: dynamic = null

    fun cyRef(): dynamic = cy

    fun renderGraph() {
        destroy()
        val container = getContainer() ?: return
        if (GraphState.allNodes.isEmpty()) { renderEmptyState(); return }
        clearContainer(container)
        cy = createInstance(container)
        populateElements()
        setupEventHandlers()
        GraphNavControls.bind(cy)
        cy.fit(null, 40)
    }

    fun renderEmptyState() {
        destroy()
        val container = getContainer() ?: return
        clearContainer(container)
        appendEmptyMessage(container)
    }

    fun destroy() { cy?.destroy(); cy = null }

    /**
     * Filter: hide/show nodes + layout visible.
     * Focus Mode → concentric layout (focused node center, neighbors in rings).
     * Non-focus ≤ 30 → circle layout. Non-focus > 30 → fit only (no re-layout).
     */
    fun applyFilter(filteredIds: Set<String>?) {
        val c = cy ?: return
        clearFocusedClass(c)
        if (filteredIds == null) {
            c.nodes().show(); c.edges().show(); c.fit(null, 40); return
        }
        applyVisibility(c, filteredIds)
        val focusId = GraphFilterPanel.getFilters().focusNodeId
        if (focusId != null) {
            applyFocusedClass(c, focusId)
            if (GraphFilterPanel.isSimplifyEnabled()) pruneNeighborEdges(c, focusId, 2)
            if (filteredIds.size <= 200) layoutConcentric(c, focusId)
            else c.fit(c.nodes(":visible"), 40)
        } else if (filteredIds.size <= 30) {
            layoutVisible(c, filteredIds.size)
        } else {
            c.fit(c.nodes(":visible"), 40)
        }
    }

    fun centerOnNode(nodeId: String) {
        val c = cy ?: return
        val node = c.getElementById(nodeId)
        if (node.length == 0) return
        c.animate(js("({fit:{eles:node,padding:80},duration:400})"))
    }

    fun resetCamera() { cy?.fit(null, 40) }

    // -- Visibility + Layout --------------------------------------------------

    private fun applyVisibility(c: dynamic, ids: Set<String>) {
        c.nodes().forEach { n: dynamic ->
            if ((n.id() as String) in ids) n.show() else n.hide()
        }
        c.edges().forEach { e: dynamic ->
            val s = e.source().id() as String
            val t = e.target().id() as String
            if (s in ids && t in ids) e.show() else e.hide()
        }
    }

    private fun applyFocusedClass(c: dynamic, focusId: String) {
        val node = c.getElementById(focusId)
        if (node.length > 0) node.addClass(CytoscapeStyles.focusedClass())
    }

    private fun clearFocusedClass(c: dynamic) {
        c.nodes(".${CytoscapeStyles.focusedClass()}").removeClass(CytoscapeStyles.focusedClass())
    }

    /**
     * Edge pruning for Focus Mode: focused node keeps ALL edges,
     * neighbor nodes keep only edges to focused node + [maxExtraEdges] others.
     */
    private fun pruneNeighborEdges(c: dynamic, focusId: String, maxExtraEdges: Int) {
        val edgeCount = HashMap<String, Int>()
        val visibleEdges = mutableListOf<dynamic>()
        c.edges(":visible").forEach { e: dynamic -> visibleEdges.add(e) }
        for (e in visibleEdges) {
            val s = e.source().id() as String
            val t = e.target().id() as String
            if (s == focusId || t == focusId) continue
            val sCount = edgeCount[s] ?: 0
            val tCount = edgeCount[t] ?: 0
            if (sCount >= maxExtraEdges && tCount >= maxExtraEdges) {
                e.hide()
            } else {
                edgeCount[s] = sCount + 1
                edgeCount[t] = tCount + 1
            }
        }
    }

    private fun layoutVisible(c: dynamic, count: Int) {
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
    private fun layoutConcentric(c: dynamic, focusId: String) {
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

    // -- Events ---------------------------------------------------------------

    private fun setupEventHandlers() {
        val c = cy ?: return
        c.on("tap", "node") { e: dynamic ->
            val id = e.target.id() as? String ?: return@on
            val node = GraphState.allNodes.find { it.id == id } ?: return@on
            GraphDetailPanel.show(node)
            GraphFilterPanel.activateFocusMode(node.id)
        }
    }

    // -- Init -----------------------------------------------------------------

    private fun createInstance(container: HTMLElement): dynamic {
        val opts = CytoscapeStyles.buildOptions()
        opts.container = container
        opts.elements = js("[]")
        return cytoscape(opts)
    }

    private fun populateElements() {
        val c = cy ?: return
        for (n in GraphState.allNodes) c.add(buildNode(n))
        for (e in GraphState.allEdges) c.add(buildEdge(e))
    }

    private fun buildNode(n: GraphNode): dynamic {
        val el = js("({})"); el.group = "nodes"
        val d = js("({})"); d.id = n.id; d.label = n.key
        d.color = GraphState.typeColorMap[n.type] ?: "#2dfecf"
        d.nodeType = n.type; d.summary = n.summary; d.clusterId = n.clusterId
        el.data = d
        val p = js("({})"); p.x = n.x; p.y = n.y; el.position = p
        return el
    }

    private fun buildEdge(e: GraphEdge): dynamic {
        val el = js("({})"); el.group = "edges"
        val d = js("({})"); d.id = "${e.sourceId}-${e.targetId}"
        d.source = e.sourceId; d.target = e.targetId
        d.color = if (e.type == "SEMANTIC") CytoscapeStyles.purpleEdge()
                  else CytoscapeStyles.cyanEdge()
        d.edgeType = e.type; el.data = d
        return el
    }

    // -- Helpers --------------------------------------------------------------

    private fun getContainer(): HTMLElement? =
        document.getElementById("graphCyContainer") as? HTMLElement

    private fun clearContainer(c: HTMLElement) { c.innerHTML = "" }

    private fun appendEmptyMessage(c: HTMLElement) {
        val msg = document.createElement("p") as HTMLElement
        msg.style.cssText = EMPTY_STYLE
        msg.textContent = "No graph data yet. Run a scan from the Dashboard."
        c.appendChild(msg)
    }

    private const val EMPTY_STYLE =
        "color:rgba(255,255,255,0.3);font-size:14px;" +
        "font-family:Be Vietnam Pro,sans-serif;text-align:center;" +
        "position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);"
}
