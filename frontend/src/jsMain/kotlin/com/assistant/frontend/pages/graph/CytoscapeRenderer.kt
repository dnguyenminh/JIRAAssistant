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

    /** True when Cytoscape instance exists (graph has been rendered at least once). */
    fun isInitialized(): Boolean = cy != null

    /**
     * Incrementally add new nodes/edges with fade-in animation (opacity 0→1).
     * Preserves existing node positions — layout runs only on new nodes.
     * Fallback: re-render full graph if cy not initialized.
     * Requirements: 2.2, 2.3
     */
    fun addElementsWithFadeIn(
        newNodes: List<GraphNode>,
        newEdges: List<GraphEdge>,
        newNodeIds: Set<String>
    ) {
        val c = cy ?: run { renderGraph(); return }
        val fadeClass = CytoscapeStyles.fadeInClass()
        val addedEles = addNewElements(c, newNodes, newEdges, fadeClass)
        CytoscapeLayoutHelper.runIncrementalLayout(c, newNodeIds)
        triggerFadeIn(addedEles, fadeClass)
        c.fit(null, 40)
    }

    /** Add nodes/edges to cy with fade-in class (opacity 0). */
    private fun addNewElements(
        c: dynamic,
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        fadeClass: String
    ): dynamic {
        val batch = js("[]")
        for (n in nodes) { val el = buildNode(n); batch.push(el) }
        for (e in edges) { val el = buildEdge(e); batch.push(el) }
        val added = c.add(batch)
        added.addClass(fadeClass)
        return added
    }

    /** Remove fade-in class after a tick so CSS transition animates opacity 0→1. */
    private fun triggerFadeIn(eles: dynamic, fadeClass: String) {
        kotlinx.browser.window.setTimeout({
            eles.removeClass(fadeClass)
        }, 50)
    }

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

    /** Filter: hide/show nodes + layout visible. Focus/concentric or circle layout. */
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

    private fun layoutVisible(c: dynamic, count: Int) =
        CytoscapeLayoutHelper.layoutVisible(c, count)

    private fun layoutConcentric(c: dynamic, focusId: String) =
        CytoscapeLayoutHelper.layoutConcentric(c, focusId)

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
