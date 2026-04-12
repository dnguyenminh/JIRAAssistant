package com.assistant.frontend.pages.graph

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AttachmentStatusDTO
import com.assistant.frontend.models.GraphNode
import com.assistant.frontend.pages.KnowledgeGraphPage
import com.assistant.frontend.services.HtmlUtils
import com.assistant.graph.LinkedTicketDTO
import com.assistant.graph.SubTaskDTO
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * Detail panel shown when clicking a graph node.
 */
internal object GraphDetailPanel {

    fun show(node: GraphNode) {
        GraphState.selectedNode = node
        val panel = document.getElementById("detailPanel") as? HTMLElement ?: return
        val content = document.getElementById("detailPanelContent") as? HTMLElement ?: return
        renderNodeInfo(content, node)
        panel.style.display = "block"
        loadAttachmentStatus(node.key)
        loadLinkedTickets(node.key)
        loadSubTasks(node.key)
    }

    fun close() {
        GraphState.selectedNode = null
        (document.getElementById("detailPanel") as? HTMLElement)?.style?.display = "none"
    }

    private fun renderNodeInfo(content: HTMLElement, node: GraphNode) {
        val color = GraphState.typeColorMap[node.type] ?: "#2dfecf"
        val typeName = when (node.type) {
            "FEATURE" -> "Feature"; "DEPENDENCY" -> "Dependency"; "UI_MODULE" -> "UI Module"; "SUB_TASK" -> "Sub Task"; else -> node.type
        }

        // Populate header: type badge, key, summary
        (document.getElementById("detail-type-dot") as? HTMLElement)?.style?.background = color
        document.getElementById("detail-type-label")?.textContent = typeName
        document.getElementById("detail-key")?.textContent = node.key
        document.getElementById("detail-summary")?.textContent = node.summary

        // Populate description
        val descEl = document.getElementById("detail-description")
        descEl?.textContent = if (node.description.isNullOrBlank()) "No description available." else node.description

        // Reset linked tickets and sub-tasks sections (hidden until loaded)
        (document.getElementById("detail-linked-section") as? HTMLElement)?.style?.display = "none"
        (document.getElementById("detail-linked-list") as? HTMLElement)?.innerHTML = ""
        (document.getElementById("detail-subtasks-section") as? HTMLElement)?.style?.display = "none"
        (document.getElementById("detail-subtasks-list") as? HTMLElement)?.innerHTML = ""

        // Reset attachments section
        (document.getElementById("attachment-status-section") as? HTMLElement)?.innerHTML = ""

        // Populate Jira link
        val jiraLinkEl = document.getElementById("detail-jira-link") as? HTMLElement
        if (jiraLinkEl != null) {
            if (!node.jiraUrl.isNullOrBlank()) {
                jiraLinkEl.innerHTML = """<a href="${HtmlUtils.escapeHtml(node.jiraUrl)}" target="_blank" rel="noopener" class="btn-vibrant" style="display:inline-block;margin-top:20px;text-decoration:none;text-align:center;font-size:12px;padding:12px 24px;">OPEN IN JIRA</a>"""
            } else {
                jiraLinkEl.innerHTML = ""
            }
        }
    }

    internal fun loadLinkedTickets(ticketKey: String) {
        val projectKey = ApiClient.getProjectKey() ?: return
        KnowledgeGraphPage.scope.launch {
            try {
                val resp = ApiClient.get("/api/projects/$projectKey/tickets/$ticketKey/links")
                if (ApiClient.handleUnauthorized(resp)) return@launch
                val body = resp.bodyAsText()
                val links = KnowledgeGraphPage.json.decodeFromString<List<LinkedTicketDTO>>(body)
                if (links.isNotEmpty()) renderLinkedTickets(links)
            } catch (e: Exception) {
                console.log("[GraphDetailPanel] Failed to load linked tickets: ${e.message}")
            }
        }
    }

    internal fun renderLinkedTickets(links: List<LinkedTicketDTO>) {
        val section = document.getElementById("detail-linked-section") as? HTMLElement ?: return
        val list = document.getElementById("detail-linked-list") as? HTMLElement ?: return
        list.innerHTML = ""
        for (link in links) {
            val row = document.createElement("div") as HTMLElement
            row.className = "detail-linked-item"
            val badge = document.createElement("span") as HTMLElement
            badge.className = "linked-relationship-badge"
            badge.textContent = link.relationship
            row.appendChild(badge)
            val keyEl = document.createElement("span") as HTMLElement
            keyEl.className = "linked-ticket-key"
            keyEl.textContent = link.key
            keyEl.addEventListener("click", { onTicketKeyClick(link.key) })
            row.appendChild(keyEl)
            val summaryEl = document.createElement("span") as HTMLElement
            summaryEl.className = "linked-ticket-summary"
            summaryEl.textContent = link.summary
            row.appendChild(summaryEl)
            list.appendChild(row)
        }
        section.style.display = ""
    }

    private fun onTicketKeyClick(ticketKey: String) {
        val node = GraphState.allNodes.find { it.key == ticketKey }
        if (node != null) {
            GraphFilterPanel.activateFocusMode(node.id)
            CytoscapeRenderer.centerOnNode(node.id)
        } else {
            console.log("[GraphDetailPanel] Node $ticketKey not found in graph")
        }
    }

    internal fun loadSubTasks(ticketKey: String) {
        val projectKey = ApiClient.getProjectKey() ?: return
        KnowledgeGraphPage.scope.launch {
            try {
                val resp = ApiClient.get("/api/projects/$projectKey/tickets/$ticketKey/subtasks")
                if (ApiClient.handleUnauthorized(resp)) return@launch
                val body = resp.bodyAsText()
                val subtasks = KnowledgeGraphPage.json.decodeFromString<List<SubTaskDTO>>(body)
                if (subtasks.isNotEmpty()) renderSubTasks(subtasks)
            } catch (e: Exception) {
                console.log("[GraphDetailPanel] Failed to load sub-tasks: ${e.message}")
            }
        }
    }

    internal fun renderSubTasks(subtasks: List<SubTaskDTO>) {
        val section = document.getElementById("detail-subtasks-section") as? HTMLElement ?: return
        val list = document.getElementById("detail-subtasks-list") as? HTMLElement ?: return
        list.innerHTML = ""
        for (task in subtasks) {
            val row = document.createElement("div") as HTMLElement
            row.className = "detail-subtask-item"
            val statusEl = document.createElement("span") as HTMLElement
            statusEl.className = "subtask-status-indicator"
            statusEl.textContent = subtaskStatusIcon(task.status)
            row.appendChild(statusEl)
            val keyEl = document.createElement("span") as HTMLElement
            keyEl.className = "linked-ticket-key"
            keyEl.textContent = task.key
            keyEl.addEventListener("click", { onTicketKeyClick(task.key) })
            row.appendChild(keyEl)
            val summaryEl = document.createElement("span") as HTMLElement
            summaryEl.className = "linked-ticket-summary"
            summaryEl.textContent = task.summary
            row.appendChild(summaryEl)
            list.appendChild(row)
        }
        section.style.display = ""
    }

    private fun subtaskStatusIcon(status: String): String = when {
        status.contains("Progress", ignoreCase = true) -> "●"
        status.contains("Done", ignoreCase = true) -> "✓"
        else -> "○"
    }

    private fun loadAttachmentStatus(ticketKey: String) {
        val projectKey = ApiClient.getProjectKey() ?: return
        KnowledgeGraphPage.scope.launch {
            try {
                val resp = ApiClient.get("/api/projects/$projectKey/tickets/$ticketKey/attachments")
                if (ApiClient.handleUnauthorized(resp)) return@launch
                val body = resp.bodyAsText()
                val statuses = KnowledgeGraphPage.json.decodeFromString<List<AttachmentStatusDTO>>(body)
                renderAttachmentStatus(statuses)
            } catch (_: Exception) { /* silently skip if endpoint unavailable */ }
        }
    }

    private fun renderAttachmentStatus(statuses: List<AttachmentStatusDTO>) {
        val section = document.getElementById("attachment-status-section") as? HTMLElement ?: return
        if (statuses.isEmpty()) return
        val container = document.createElement("div") as HTMLElement
        container.style.cssText = "border-top:1px solid rgba(255,255,255,0.06);padding-top:16px;margin-top:16px;"
        val header = document.createElement("div") as HTMLElement
        header.style.cssText = "font-size:11px;font-weight:700;letter-spacing:1.5px;opacity:0.4;margin-bottom:8px;"
        header.textContent = "ATTACHMENTS"
        container.appendChild(header)
        for (att in statuses) {
            val row = document.createElement("div") as HTMLElement
            row.style.cssText = "font-size:13px;opacity:0.7;padding:4px 0;display:flex;align-items:center;gap:6px;"
            val icon = statusIcon(att.status)
            row.textContent = "$icon ${att.filename} (${att.chunkCount} chunks)"
            container.appendChild(row)
        }
        section.appendChild(container)
    }

    private fun statusIcon(status: String): String = when (status) {
        "CONVERTED" -> "✅"
        "PENDING" -> "⏳"
        "FAILED" -> "❌"
        else -> "❓"
    }
}
