package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.router.Router
import com.assistant.frontend.services.HtmlUtils
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Standalone project selection page with sortable, searchable, paginated table.
 * Requirements: 1.5
 */
object ProjectSelectPage {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var allProjects = listOf<JiraProject>()
    private var filtered = listOf<JiraProject>()
    private var sortCol = "key"
    private var sortAsc = true
    private var page = 0
    private const val PAGE_SIZE = 20

    @Serializable
    data class JiraProject(
        val key: String = "",
        val name: String = "",
        val projectTypeKey: String = ""
    )

    fun render(container: Element) {
        container.innerHTML = ""
        scope.launch {
            val html = ApiClient.loadTemplate("project-select")
            container.innerHTML = html
            bindEvents()
            loadProjects()
        }
    }

    private fun bindEvents() {
        document.getElementById("project-search")?.addEventListener("input", {
            applyFilter(); renderTable()
        })
        document.querySelectorAll("#project-table th[data-sort]").let { headers ->
            for (i in 0 until headers.length) {
                val th = headers.item(i) as? HTMLElement ?: continue
                th.addEventListener("click", {
                    val col = th.getAttribute("data-sort") ?: return@addEventListener
                    if (sortCol == col) sortAsc = !sortAsc else { sortCol = col; sortAsc = true }
                    applySort(); renderTable(); updateHeaders()
                })
            }
        }
        document.getElementById("btn-prev-page")?.addEventListener("click", {
            if (page > 0) { page--; renderTable() }
        })
        document.getElementById("btn-next-page")?.addEventListener("click", {
            if ((page + 1) * PAGE_SIZE < filtered.size) { page++; renderTable() }
        })
    }

    private fun loadProjects() {
        BlockingOverlay.show("project-select-page", "Loading projects...")
        scope.launch {
            try {
                val resp = ApiClient.get("/api/projects")
                if (ApiClient.handleUnauthorized(resp)) {
                    Router.navigateTo("login"); return@launch
                }
                if (resp.status == HttpStatusCode.OK) {
                    allProjects = json.decodeFromString<List<JiraProject>>(resp.bodyAsText())
                    applyFilter(); applySort(); renderTable()
                } else {
                    renderEmpty("Failed to load projects (${resp.status})")
                }
            } catch (e: Exception) {
                renderEmpty("Connection error: ${e.message}")
            } finally {
                BlockingOverlay.remove("project-select-page")
            }
        }
    }

    private fun applyFilter() {
        val q = (document.getElementById("project-search") as? HTMLInputElement)
            ?.value?.trim()?.lowercase() ?: ""
        filtered = if (q.isBlank()) allProjects
        else allProjects.filter {
            it.key.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
        page = 0
    }

    private fun applySort() {
        filtered = when (sortCol) {
            "key" -> if (sortAsc) filtered.sortedBy { it.key } else filtered.sortedByDescending { it.key }
            "name" -> if (sortAsc) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
            "type" -> if (sortAsc) filtered.sortedBy { it.projectTypeKey } else filtered.sortedByDescending { it.projectTypeKey }
            else -> filtered
        }
    }

    private fun renderTable() {
        val tbody = document.getElementById("project-table-body") as? HTMLElement ?: return
        tbody.innerHTML = ""
        if (filtered.isEmpty()) {
            renderEmpty("No projects match your search."); return
        }
        val start = page * PAGE_SIZE
        val pageItems = filtered.drop(start).take(PAGE_SIZE)
        for (p in pageItems) {
            val tr = document.createElement("tr") as HTMLElement
            tr.style.cssText = "border-bottom:1px solid rgba(255,255,255,0.04);cursor:pointer;transition:background 0.2s;"
            tr.innerHTML = """
                <td style="padding:12px 16px;font-weight:600;color:var(--primary);">${HtmlUtils.escapeHtml(p.key)}</td>
                <td style="padding:12px 16px;">${HtmlUtils.escapeHtml(p.name)}</td>
                <td style="padding:12px 16px;opacity:0.5;">${HtmlUtils.escapeHtml(p.projectTypeKey)}</td>
            """.trimIndent()
            tr.addEventListener("mouseenter", { tr.style.background = "rgba(45,254,207,0.06)" })
            tr.addEventListener("mouseleave", { tr.style.background = "" })
            tr.addEventListener("click", { selectProject(p.key) })
            tbody.appendChild(tr)
        }
        updatePagination()
    }

    private fun updatePagination() {
        val totalPages = ((filtered.size - 1) / PAGE_SIZE) + 1
        (document.getElementById("page-info") as? HTMLElement)?.textContent = "Page ${page + 1} of $totalPages — ${filtered.size} projects"
        (document.getElementById("btn-prev-page") as? HTMLElement)?.let {
            if (page > 0) it.removeAttribute("disabled") else it.setAttribute("disabled", "true")
        }
        (document.getElementById("btn-next-page") as? HTMLElement)?.let {
            if ((page + 1) * PAGE_SIZE < filtered.size) it.removeAttribute("disabled") else it.setAttribute("disabled", "true")
        }
    }

    private fun updateHeaders() {
        document.querySelectorAll("#project-table th[data-sort]").let { headers ->
            for (i in 0 until headers.length) {
                val th = headers.item(i) as? HTMLElement ?: continue
                val col = th.getAttribute("data-sort") ?: continue
                val base = when (col) { "key" -> "KEY"; "name" -> "PROJECT NAME"; "type" -> "TYPE"; else -> col }
                th.textContent = if (col == sortCol) "$base ${if (sortAsc) "▲" else "▼"}" else base
            }
        }
    }

    private fun renderEmpty(msg: String) {
        val tbody = document.getElementById("project-table-body") as? HTMLElement ?: return
        tbody.innerHTML = "<tr><td colspan='3' style='text-align:center;padding:48px;opacity:0.5;'>$msg</td></tr>"
    }

    private fun selectProject(key: String) {
        ApiClient.saveProjectKey(key)
        Router.navigateTo("dashboard")
    }
}
