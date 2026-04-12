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
 * Scan status polling and progressive graph loading.
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
                    if (status.status == ScanStatus.COMPLETED || status.status == ScanStatus.CANCELLED || status.status == ScanStatus.IDLE) {
                        stopGraphPolling(); break
                    }
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
                    val response = ApiClient.get("/api/graph/$projectKey")
                    if (ApiClient.handleUnauthorized(response)) break
                    if (response.status.value != 200) continue
                    val body = response.bodyAsText()
                    val graphData = KnowledgeGraphPage.json.decodeFromString<GraphLayoutResponse>(body)
                    val existingNodeIds = GraphState.allNodes.map { it.id }.toSet()
                    val existingEdgeKeys = GraphState.allEdges.map { "${it.sourceId}-${it.targetId}" }.toSet()
                    val addedNodes = graphData.nodes.filter { it.id !in existingNodeIds }
                    val addedEdges = graphData.edges.filter { "${it.sourceId}-${it.targetId}" !in existingEdgeKeys }
                    if (addedNodes.isNotEmpty() || addedEdges.isNotEmpty()) {
                        GraphState.allNodes = graphData.nodes
                        GraphState.allEdges = graphData.edges
                        GraphState.allClusters = graphData.clusters ?: emptyList()
                        GraphState.filteredNodeIds = GraphState.allNodes.map { it.id }.toSet()
                        renderGraphWithFadeIn(addedNodes.map { it.id }.toSet(), addedEdges.map { "${it.sourceId}-${it.targetId}" }.toSet())
                        KnowledgeGraphPage.updateNodeCount()
                    }
                } catch (e: Exception) {
                    console.log("[KnowledgeGraphPage] Graph polling error: ${e.message}"); break
                }
            }
        }
    }

    private fun stopGraphPolling() { graphPollingJob?.cancel(); graphPollingJob = null }

    private fun renderGraphWithFadeIn(newNodeIds: Set<String>, newEdgeKeys: Set<String>) {
        if (GraphState.allNodes.isEmpty()) { CytoscapeRenderer.renderEmptyState(); return }
        // Re-render full graph with updated data (Cytoscape handles layout)
        CytoscapeRenderer.renderGraph()
    }
}
