package com.assistant.frontend.pages.graph

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GraphLayoutResponse
import com.assistant.frontend.models.ScanStatusResponse
import com.assistant.frontend.pages.KnowledgeGraphPage
import com.assistant.frontend.services.ScanStatusService
import com.assistant.scan.ScanStatus
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * Scan status polling and diff-based progressive graph loading.
 * Polls graph API every 5s during scan, incrementally adds new elements.
 */
internal object GraphScanStatus {

    private var scanStatusPollingJob: Job? = null
    private var graphPollingJob: Job? = null

    fun cancelJobs() {
        scanStatusPollingJob?.cancel(); scanStatusPollingJob = null
        graphPollingJob?.cancel(); graphPollingJob = null
    }

    fun loadScanStatus() {
        val projectKey = ApiClient.getProjectKey() ?: return
        KnowledgeGraphPage.scope.launch {
            try {
                val response = ApiClient.get("/api/projects/$projectKey/scan/status")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val status = KnowledgeGraphPage.json.decodeFromString<ScanStatusResponse>(body)
                updateBadge(status)
                if (status.status == ScanStatus.SCANNING) {
                    if (GraphState.allNodes.isEmpty()) showScanningLoadingState()
                    startScanStatusPolling(); startGraphPolling()
                }
            } catch (e: Exception) {
                console.log("[KnowledgeGraphPage] Failed to load scan status: ${e.message}")
            }
        }
    }

    private fun updateBadge(status: ScanStatusResponse) {
        val badge = document.getElementById("graphScanStatusBadge") as? HTMLElement ?: return
        ScanStatusService.updateBadge(badge, status.status, status.processedCount, status.totalTickets)
    }

    private fun startScanStatusPolling() {
        if (scanStatusPollingJob?.isActive == true) return
        scanStatusPollingJob = KnowledgeGraphPage.scope.launch {
            while (true) {
                delay(3000)
                val projectKey = ApiClient.getProjectKey() ?: break
                try {
                    val response = ApiClient.get("/api/projects/$projectKey/scan/status")
                    if (ApiClient.handleUnauthorized(response)) break
                    val body = response.bodyAsText()
                    val status = KnowledgeGraphPage.json.decodeFromString<ScanStatusResponse>(body)
                    updateBadge(status)
                    if (isScanTerminal(status.status)) { stopGraphPolling(); break }
                } catch (e: Exception) {
                    console.log("[KnowledgeGraphPage] Scan polling error: ${e.message}"); break
                }
            }
        }
    }

    private fun startGraphPolling() {
        if (graphPollingJob?.isActive == true) return
        graphPollingJob = KnowledgeGraphPage.scope.launch {
            while (true) {
                delay(5000)
                val projectKey = ApiClient.getProjectKey() ?: break
                try {
                    val result = pollGraphOnce(projectKey) ?: continue
                    if (result.completed) { performFinalLoad(projectKey); break }
                } catch (e: Exception) {
                    console.log("[KnowledgeGraphPage] Graph polling error: ${e.message}")
                    // Continue polling — transient errors auto-recover on next interval
                }
            }
        }
    }

    /** Single poll iteration. Returns null if no action needed, PollResult otherwise. */
    private suspend fun pollGraphOnce(projectKey: String): PollResult? {
        val response = ApiClient.get("/api/graph/$projectKey")
        if (ApiClient.handleUnauthorized(response)) return PollResult(completed = true)
        if (response.status.value != 200) return null
        val body = response.bodyAsText()
        val graphData = KnowledgeGraphPage.json.decodeFromString<GraphLayoutResponse>(body)
        if (graphData.nodes.isEmpty()) return null
        val diff = computeDiff(graphData)
        if (diff.hasNewElements) applyIncrementalUpdate(graphData, diff)
        return checkScanCompleted(projectKey)
    }

    /** Compare node/edge IDs between response and current GraphState. */
    internal fun computeDiff(graphData: GraphLayoutResponse): GraphDiff {
        val existingNodeIds = GraphState.allNodes.map { it.id }.toSet()
        val existingEdgeKeys = GraphState.allEdges.map { edgeKey(it) }.toSet()
        val newNodes = graphData.nodes.filter { it.id !in existingNodeIds }
        val newEdges = graphData.edges.filter { edgeKey(it) !in existingEdgeKeys }
        return GraphDiff(newNodes, newEdges, existingNodeIds)
    }

    /** Apply diff: first poll → full render, subsequent → incremental fade-in. */
    private fun applyIncrementalUpdate(graphData: GraphLayoutResponse, diff: GraphDiff) {
        updateGraphState(graphData)
        GraphStateManager.save(graphData)
        if (diff.previousNodeIds.isEmpty() || !CytoscapeRenderer.isInitialized()) {
            CytoscapeRenderer.renderGraph()
        } else {
            val newNodeIds = diff.newNodes.map { it.id }.toSet()
            CytoscapeRenderer.addElementsWithFadeIn(diff.newNodes, diff.newEdges, newNodeIds)
        }
        KnowledgeGraphPage.updateNodeCount()
    }

    /** Check if scan completed — if so, signal to stop polling. */
    private suspend fun checkScanCompleted(projectKey: String): PollResult {
        val statusResp = ApiClient.get("/api/projects/$projectKey/scan/status")
        if (statusResp.status.value != 200) return PollResult(completed = false)
        val body = statusResp.bodyAsText()
        val status = KnowledgeGraphPage.json.decodeFromString<ScanStatusResponse>(body)
        updateBadge(status)
        return PollResult(completed = isScanTerminal(status.status))
    }

    /** Final load on COMPLETED: full renderGraph() to ensure complete data. */
    private fun performFinalLoad(projectKey: String) {
        stopGraphPolling()
        KnowledgeGraphPage.loadGraphData()
    }

    private fun updateGraphState(graphData: GraphLayoutResponse) {
        GraphState.allNodes = graphData.nodes
        GraphState.allEdges = graphData.edges
        GraphState.allClusters = graphData.clusters ?: emptyList()
        GraphState.filteredNodeIds = GraphState.allNodes.map { it.id }.toSet()
    }

    /** Show loading message in graph container when scan is active but no data yet. Req 3.3 */
    internal fun showScanningLoadingState() {
        val container = document.getElementById("graphCyContainer") as? HTMLElement ?: return
        container.innerHTML = ""
        val msg = document.createElement("p") as HTMLElement
        msg.id = "graphScanLoadingMsg"
        msg.style.cssText = LOADING_MSG_STYLE
        msg.textContent = "Đang scan, đồ thị sẽ xuất hiện khi ticket đầu tiên được analyze..."
        container.appendChild(msg)
    }

    private fun stopGraphPolling() { graphPollingJob?.cancel(); graphPollingJob = null }

    private fun isScanTerminal(status: ScanStatus): Boolean =
        status == ScanStatus.COMPLETED || status == ScanStatus.CANCELLED || status == ScanStatus.IDLE

    private fun edgeKey(e: com.assistant.frontend.models.GraphEdge): String =
        "${e.sourceId}-${e.targetId}"

    /** Result of a single graph poll iteration. */
    internal data class PollResult(val completed: Boolean)

    /** Diff between new graph response and current state. */
    internal data class GraphDiff(
        val newNodes: List<com.assistant.frontend.models.GraphNode>,
        val newEdges: List<com.assistant.frontend.models.GraphEdge>,
        val previousNodeIds: Set<String>
    ) {
        val hasNewElements: Boolean get() = newNodes.isNotEmpty() || newEdges.isNotEmpty()
    }

    private const val LOADING_MSG_STYLE =
        "color:var(--primary);font-size:14px;font-family:Be Vietnam Pro,sans-serif;" +
        "text-align:center;position:absolute;top:50%;left:50%;" +
        "transform:translate(-50%,-50%);opacity:0.7;"
}
