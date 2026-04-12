package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphNode
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Combobox search with grouped results, match-type icons,
 * and filter checkboxes. Max 100 suggestions.
 */
internal object GraphSearchCombobox {

    private var debounceId: Int? = null
    private const val DEBOUNCE_MS = 120
    private const val MAX_RESULTS = 100
    private var activeIndex = -1

    private val enabledGroups = mutableSetOf("key", "summary", "description")

    fun init() {
        val input = input() ?: return
        input.addEventListener("input", { debouncedUpdate() })
        input.addEventListener("focus", { showIfHasQuery() })
        input.addEventListener("keydown", { e -> handleKeydown(e) })
        document.addEventListener("click", { e -> handleOutsideClick(e) })
    }

    private fun debouncedUpdate() {
        debounceId?.let { window.clearTimeout(it) }
        debounceId = window.setTimeout({ updateDropdown() }, DEBOUNCE_MS)
    }

    private fun updateDropdown() {
        val query = input()?.value?.trim() ?: ""
        if (query.length < 2) { hide(); GraphFilterPanel.onFilterChange(); return }
        val results = searchNodes(query)
        if (results.isEmpty()) { hide(); GraphFilterPanel.onFilterChange(); return }
        renderDropdown(results)
    }

    private data class SearchResult(val node: GraphNode, val matchType: String, val matchText: String)

    private fun searchNodes(query: String): List<SearchResult> {
        val q = query.lowercase()
        val results = mutableListOf<SearchResult>()
        for (n in GraphState.allNodes) {
            if (results.size >= MAX_RESULTS) break
            if ("key" in enabledGroups && n.key.lowercase().contains(q))
                results.add(SearchResult(n, "key", n.key))
            else if ("summary" in enabledGroups && n.summary.lowercase().contains(q))
                results.add(SearchResult(n, "summary", n.summary))
            else if ("description" in enabledGroups && n.description?.lowercase()?.contains(q) == true)
                results.add(SearchResult(n, "description", truncate(n.description!!, q)))
        }
        return results
    }

    private fun truncate(text: String, query: String): String {
        val idx = text.lowercase().indexOf(query)
        val start = maxOf(0, idx - 30)
        val end = minOf(text.length, idx + query.length + 40)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix${text.substring(start, end)}$suffix"
    }

    private fun renderDropdown(results: List<SearchResult>) {
        val dd = dropdown() ?: return
        dd.innerHTML = ""
        activeIndex = -1
        dd.appendChild(buildFilterBar())
        val grouped = results.groupBy { it.matchType }
        for ((group, items) in grouped) {
            dd.appendChild(buildGroupHeader(group, items.size))
            for (r in items) dd.appendChild(buildResultItem(r))
        }
        dd.style.display = ""
    }

    private fun buildFilterBar(): HTMLElement {
        val bar = document.createElement("div") as HTMLElement
        bar.style.cssText = "display:flex;gap:12px;padding:8px 12px;border-bottom:1px solid var(--glass-border);position:sticky;top:0;background:rgba(12,14,22,0.97);z-index:1;"
        for ((id, label, icon) in listOf(
            Triple("key", "Key", "🔑"), Triple("summary", "Summary", "📝"), Triple("description", "Description", "📄")
        )) {
            val cb = document.createElement("label") as HTMLElement
            cb.style.cssText = "display:flex;align-items:center;gap:4px;font-size:11px;cursor:pointer;color:var(--text-sub);user-select:none;"
            val checked = if (id in enabledGroups) "checked" else ""
            cb.innerHTML = """<input type="checkbox" id="search-filter-$id" $checked style="width:12px;height:12px;cursor:pointer;" />$icon $label"""
            cb.querySelector("input")?.addEventListener("change", { toggleGroup(id) })
            bar.appendChild(cb)
        }
        return bar
    }

    private fun buildGroupHeader(group: String, count: Int): HTMLElement {
        val h = document.createElement("div") as HTMLElement
        val icon = when (group) { "key" -> "🔑"; "summary" -> "📝"; "description" -> "📄"; else -> "📎" }
        h.style.cssText = "padding:6px 12px;font-size:10px;font-weight:700;letter-spacing:1px;opacity:0.4;text-transform:uppercase;border-top:1px solid var(--glass-border);"
        h.textContent = "$icon $group ($count)"
        return h
    }

    private fun buildResultItem(r: SearchResult): HTMLElement {
        val item = document.createElement("div") as HTMLElement
        item.className = "search-dropdown-item"
        val icon = when (r.matchType) { "key" -> "🔑"; "summary" -> "📝"; "description" -> "📄"; else -> "📎" }
        item.innerHTML = """<span style="flex-shrink:0;">$icon</span><span class="item-key">${r.node.key}</span><span class="item-summary">${escapeHtml(r.matchText)}</span>"""
        item.addEventListener("click", { selectItem(r.node.key) })
        return item
    }

    private fun toggleGroup(group: String) {
        if (group in enabledGroups) enabledGroups.remove(group) else enabledGroups.add(group)
        updateDropdown()
    }

    private fun selectItem(key: String) {
        val inp = input() ?: return
        inp.value = key
        hide()
        GraphFilterPanel.onFilterChange()
    }

    private fun handleKeydown(e: dynamic) {
        val dd = dropdown() ?: return
        val items = dd.querySelectorAll(".search-dropdown-item")
        val count = items.length as Int
        when (e.key as? String) {
            "ArrowDown" -> { e.preventDefault(); if (count > 0) moveActive(items, count, 1) }
            "ArrowUp" -> { e.preventDefault(); if (count > 0) moveActive(items, count, -1) }
            "Enter" -> {
                e.preventDefault()
                if (activeIndex in 0 until count) {
                    val key = (items.item(activeIndex) as? HTMLElement)?.querySelector(".item-key")?.textContent ?: return
                    selectItem(key)
                } else { hide(); GraphFilterPanel.onFilterChange() }
            }
            "Escape" -> hide()
        }
    }

    private fun moveActive(items: dynamic, count: Int, delta: Int) {
        if (activeIndex in 0 until count)
            (items.item(activeIndex) as? HTMLElement)?.classList?.remove("active")
        activeIndex = ((activeIndex + delta) % count + count) % count
        val el = items.item(activeIndex) as? HTMLElement
        el?.classList?.add("active")
        el?.scrollIntoView(js("({block:'nearest'})"))
    }

    private fun showIfHasQuery() {
        if ((input()?.value?.trim()?.length ?: 0) >= 2) updateDropdown()
    }

    private fun handleOutsideClick(e: dynamic) {
        val wrapper = document.getElementById("graphSearchWrapper") ?: return
        if (!(wrapper.contains(e.target as? org.w3c.dom.Node) as Boolean)) hide()
    }

    private fun hide() { dropdown()?.style?.display = "none"; activeIndex = -1 }

    private fun input(): HTMLInputElement? = document.getElementById("graphSearchInput") as? HTMLInputElement
    private fun dropdown(): HTMLElement? = document.getElementById("graphSearchDropdown") as? HTMLElement

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
