package com.assistant.frontend.pages.graph

import com.assistant.frontend.components.AIChatSidebar
import com.assistant.frontend.models.GraphCluster
import com.assistant.frontend.models.GraphFilters
import com.assistant.frontend.models.NodeTypeInfo
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement

/**
 * UI controller for the filter panel — binds DOM events, reads filter state,
 * delegates computation to [GraphFilterEngine], and triggers CytoscapeRenderer filter.
 * Node type checkboxes are dynamically created from backend data.
 */
internal object GraphFilterPanel {

    private var sliderTimerId: Int? = null
    private const val DEBOUNCE_MS = 150

    /** Dynamic mapping: checkbox element ID → node type string. */
    private var dynamicTypeMap: Map<String, String> = emptyMap()

    fun init() {
        bindClusterDropdown()
        bindDepthSlider()
        bindShowAllButton()
        bindFocusClose()
        bindSimplifyToggle()
    }

    /** Create checkboxes dynamically from backend nodeTypes data. */
    fun populateNodeTypes(types: List<NodeTypeInfo>) {
        val container = document.getElementById("filter-type-container") as? HTMLElement ?: return
        container.innerHTML = ""
        val map = mutableMapOf<String, String>()
        for (info in types) {
            val cbId = "filter-type-${info.type.lowercase().replace(" ", "-")}"
            map[cbId] = info.type
            container.appendChild(buildTypeCheckbox(cbId, info))
        }
        dynamicTypeMap = map
        bindCheckboxes()
    }

    /** Recalculate visible nodes from current DOM state and refresh graph. */
    fun onFilterChange() {
        val filters = getFilters()
        val focusedIds = computeFocusSet(filters)
        val visible = GraphFilterEngine.computeVisibleNodes(filters, GraphState.allNodes, focusedIds)
        GraphState.filteredNodeIds = visible
        CytoscapeRenderer.applyFilter(if (visible.size == GraphState.allNodes.size) null else visible)
        updateNodeCount(visible.size, GraphState.allNodes.size)
        notifyChatSidebar()
    }

    fun activateFocusMode(nodeId: String) {
        val node = GraphState.allNodes.find { it.id == nodeId }
        showFocusIndicator(node?.key ?: nodeId)
        enableDepthSlider()
        enableSimplify()
        setFocusNodeId(nodeId)
        onFilterChange()
        CytoscapeRenderer.centerOnNode(nodeId)
    }

    fun deactivateFocusMode() {
        hideFocusIndicator(); disableDepthSlider(); disableSimplify(); setFocusNodeId(null)
    }

    /** Reset all filters to defaults and refresh. */
    fun resetAll() {
        dynamicTypeMap.keys.forEach { (document.getElementById(it) as? HTMLInputElement)?.checked = true }
        (document.getElementById("filter-cluster") as? HTMLSelectElement)?.value = ""
        (document.getElementById("graphSearchInput") as? HTMLInputElement)?.value = ""
        deactivateFocusMode(); onFilterChange(); CytoscapeRenderer.resetCamera()
    }

    fun updateNodeCount(visible: Int, total: Int) {
        (document.getElementById("node-count") as? HTMLElement)?.textContent = "$visible / $total nodes"
    }

    /** Read current filter state from DOM elements. */
    fun getFilters(): GraphFilters {
        val types = readEnabledTypes()
        val cluster = readClusterId()
        val search = (document.getElementById("graphSearchInput") as? HTMLInputElement)?.value ?: ""
        val depth = (document.getElementById("filter-depth") as? HTMLInputElement)?.value?.toIntOrNull() ?: 1
        return GraphFilters(types, cluster, currentFocusNodeId, depth, search)
    }

    fun populateClusters(clusters: List<GraphCluster>) {
        val select = document.getElementById("filter-cluster") as? HTMLSelectElement ?: return
        clearClusterOptions(select)
        addDefaultClusterOption(select)
        val nodeCounts = countNodesPerCluster()
        clusters.filter { (nodeCounts[it.id] ?: 0) >= 2 }
            .sortedByDescending { nodeCounts[it.id] ?: 0 }
            .forEach { addClusterOption(select, it, nodeCounts[it.id] ?: 0) }
    }

    /** All known type strings (for isAnyFilterActive comparison). */
    fun allTypeNames(): Set<String> = dynamicTypeMap.values.toSet()

    private var currentFocusNodeId: String? = null
    private fun setFocusNodeId(nodeId: String?) { currentFocusNodeId = nodeId }

    private fun buildTypeCheckbox(cbId: String, info: NodeTypeInfo): HTMLElement {
        val label = document.createElement("label") as HTMLElement
        label.style.cssText = "display:flex;align-items:center;gap:6px;cursor:pointer;user-select:none;"
        label.innerHTML = """
            <input type="checkbox" id="$cbId" checked style="accent-color:${info.color};width:14px;height:14px;cursor:pointer;" />
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${info.color};"></span>
            <span style="color:var(--text-sub);font-weight:500;">${info.type}</span>
        """.trimIndent()
        return label
    }

    private fun bindCheckboxes() {
        dynamicTypeMap.keys.forEach { document.getElementById(it)?.addEventListener("change", { onFilterChange() }) }
    }

    private fun bindClusterDropdown() {
        document.getElementById("filter-cluster")?.addEventListener("change", {
            updateClusterHighlight(); onFilterChange()
        })
    }

    private fun bindDepthSlider() {
        document.getElementById("filter-depth")?.addEventListener("input", { debouncedFilterChange() })
    }

    private fun bindShowAllButton() {
        document.getElementById("btn-show-all")?.addEventListener("click", { resetAll() })
    }

    private fun bindFocusClose() {
        document.getElementById("focus-indicator-close")?.addEventListener("click", {
            deactivateFocusMode(); onFilterChange()
        })
    }

    private fun debouncedFilterChange() {
        sliderTimerId?.let { window.clearTimeout(it) }
        sliderTimerId = window.setTimeout({ onFilterChange() }, DEBOUNCE_MS)
        updateDepthLabel()
    }

    private fun updateDepthLabel() {
        val value = (document.getElementById("filter-depth") as? HTMLInputElement)?.value ?: "1"
        (document.getElementById("filter-depth-label") as? HTMLElement)?.textContent = "Depth: $value hops"
    }

    private fun readEnabledTypes(): Set<String> =
        dynamicTypeMap.entries
            .filter { (document.getElementById(it.key) as? HTMLInputElement)?.checked == true }
            .mapTo(mutableSetOf()) { it.value }

    private fun readClusterId(): Int? =
        (document.getElementById("filter-cluster") as? HTMLSelectElement)?.value
            ?.takeIf { it.isNotBlank() }?.toIntOrNull()

    private fun showFocusIndicator(key: String) {
        (document.getElementById("focus-indicator") as? HTMLElement)?.style?.display = "flex"
        (document.getElementById("focus-indicator-key") as? HTMLElement)?.textContent = key
    }

    private fun hideFocusIndicator() {
        (document.getElementById("focus-indicator") as? HTMLElement)?.style?.display = "none"
        (document.getElementById("focus-indicator-key") as? HTMLElement)?.textContent = ""
    }

    private fun enableDepthSlider() {
        val s = document.getElementById("filter-depth") as? HTMLInputElement ?: return
        s.disabled = false; s.style.opacity = "1"
    }

    private fun disableDepthSlider() {
        val s = document.getElementById("filter-depth") as? HTMLInputElement ?: return
        s.disabled = true; s.value = "1"; s.style.opacity = "0.4"; updateDepthLabel()
    }

    fun isSimplifyEnabled(): Boolean =
        (document.getElementById("filter-simplify") as? HTMLInputElement)?.checked == true

    private fun enableSimplify() {
        val cb = document.getElementById("filter-simplify") as? HTMLInputElement ?: return
        cb.disabled = false
        (document.getElementById("simplify-group") as? HTMLElement)?.style?.opacity = "1"
    }

    private fun disableSimplify() {
        val cb = document.getElementById("filter-simplify") as? HTMLInputElement ?: return
        cb.disabled = true; cb.checked = false
        (document.getElementById("simplify-group") as? HTMLElement)?.style?.opacity = "0.4"
    }

    private fun bindSimplifyToggle() {
        document.getElementById("filter-simplify")?.addEventListener("change", { onFilterChange() })
    }

    private fun computeFocusSet(filters: GraphFilters): Set<String> {
        val focusId = filters.focusNodeId ?: return emptySet()
        return GraphFilterEngine.bfsFromEdges(focusId, filters.focusDepth, GraphState.allEdges)
    }

    private fun notifyChatSidebar() { AIChatSidebar.updateGraphContext() }

    private fun clearClusterOptions(select: HTMLSelectElement) {
        while (select.options.length > 0) select.remove(0)
    }

    private fun updateClusterHighlight() {
        val select = document.getElementById("filter-cluster") as? HTMLElement ?: return
        val value = (select as? HTMLSelectElement)?.value ?: ""
        if (value.isNotBlank()) select.classList.add("has-selection")
        else select.classList.remove("has-selection")
    }

    private fun addDefaultClusterOption(select: HTMLSelectElement) {
        val opt = document.createElement("option") as HTMLOptionElement
        opt.value = ""; opt.textContent = "All Clusters"; select.appendChild(opt)
    }

    private fun addClusterOption(select: HTMLSelectElement, c: GraphCluster, nodeCount: Int) {
        val opt = document.createElement("option") as HTMLOptionElement
        opt.value = c.id.toString()
        val label = c.label ?: "Cluster ${c.id}"
        opt.textContent = "$label  ·  $nodeCount nodes"
        opt.style.color = c.color
        select.appendChild(opt)
    }

    private fun countNodesPerCluster(): Map<Int, Int> =
        GraphState.allNodes.mapNotNull { it.clusterId }.groupingBy { it }.eachCount()
}
