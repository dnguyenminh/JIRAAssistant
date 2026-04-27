package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.ApiSpecification
import com.assistant.ai.deepanalysis.models.DatabaseChange
import com.assistant.ai.deepanalysis.models.ExternalIntegration
import com.assistant.ai.deepanalysis.models.TechnicalDetails
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Renders Technical Details section: API specs table, DB changes table,
 * and external integrations list. Split from ContextTabRenderer for SRP.
 * Requirements: 22.1
 */
internal object TechnicalDetailsRenderer {

    fun render(parent: HTMLElement, details: TechnicalDetails) {
        val hasData = details.apiSpecifications.isNotEmpty() ||
            details.databaseChanges.isNotEmpty() ||
            details.externalIntegrations.isNotEmpty()
        if (!hasData) return
        val card = ContextTabRenderer.createSection(parent, "TECHNICAL DETAILS")
        renderApiTable(card, details.apiSpecifications)
        renderDbTable(card, details.databaseChanges)
        renderIntegrationsList(card, details.externalIntegrations)
    }

    private fun renderApiTable(parent: HTMLElement, apis: List<ApiSpecification>) {
        if (apis.isEmpty()) return
        val label = createSubLabel("API SPECIFICATIONS")
        parent.appendChild(label)
        val table = createTable(listOf("METHOD", "PATH", "DESCRIPTION"))
        val tbody = document.createElement("tbody")
        apis.forEach { api ->
            val row = document.createElement("tr")
            row.appendChild(createMethodCell(api.method))
            row.appendChild(createTextCell(api.path, true))
            row.appendChild(createTextCell(api.description, false))
            tbody.appendChild(row)
        }
        table.appendChild(tbody)
        parent.appendChild(table)
    }

    private fun renderDbTable(parent: HTMLElement, changes: List<DatabaseChange>) {
        if (changes.isEmpty()) return
        val label = createSubLabel("DATABASE CHANGES")
        parent.appendChild(label)
        val table = createTable(listOf("TABLE", "OPERATION", "COLUMNS"))
        val tbody = document.createElement("tbody")
        changes.forEach { ch ->
            val row = document.createElement("tr")
            row.appendChild(createTextCell(ch.tableName, true))
            row.appendChild(createOperationCell(ch.operationType))
            row.appendChild(createTextCell(ch.columns.joinToString(", "), false))
            tbody.appendChild(row)
        }
        table.appendChild(tbody)
        parent.appendChild(table)
    }

    private fun renderIntegrationsList(
        parent: HTMLElement, integrations: List<ExternalIntegration>
    ) {
        if (integrations.isEmpty()) return
        val label = createSubLabel("EXTERNAL INTEGRATIONS")
        parent.appendChild(label)
        val list = document.createElement("div") as HTMLElement
        list.style.cssText = "display:flex;flex-direction:column;gap:8px;"
        integrations.forEach { intg ->
            list.appendChild(createIntegrationItem(intg))
        }
        parent.appendChild(list)
    }

    private fun createIntegrationItem(intg: ExternalIntegration): Element {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;align-items:center;gap:12px;padding:10px 14px;border-radius:8px;background:rgba(255,255,255,0.02);border:1px solid var(--glass-border);"
        val name = document.createElement("span") as HTMLElement
        name.textContent = intg.serviceName
        name.style.cssText = "font-weight:600;font-size:13px;color:var(--primary);min-width:120px;"
        val proto = document.createElement("span") as HTMLElement
        proto.textContent = intg.protocol
        proto.style.cssText = "font-size:11px;padding:2px 8px;border-radius:4px;background:rgba(51,134,255,0.1);color:var(--accent);font-weight:600;"
        val ep = document.createElement("span") as HTMLElement
        ep.textContent = intg.endpoint
        ep.style.cssText = "font-size:12px;opacity:0.7;font-family:'JetBrains Mono',monospace;"
        row.appendChild(name)
        row.appendChild(proto)
        row.appendChild(ep)
        return row
    }

    private fun createSubLabel(text: String): Element {
        val el = document.createElement("div") as HTMLElement
        el.textContent = text
        el.style.cssText = "font-size:10px;font-weight:700;letter-spacing:2px;opacity:0.35;margin:20px 0 10px;"
        return el
    }

    private fun createTable(headers: List<String>): Element {
        val table = document.createElement("table") as HTMLElement
        table.style.cssText = "width:100%;border-collapse:collapse;font-size:12px;"
        val thead = document.createElement("thead")
        val headerRow = document.createElement("tr")
        headers.forEach { h ->
            val th = document.createElement("th") as HTMLElement
            th.textContent = h
            th.style.cssText = "text-align:left;padding:8px 12px;font-size:10px;font-weight:700;letter-spacing:1.5px;opacity:0.4;border-bottom:1px solid var(--glass-border);"
            headerRow.appendChild(th)
        }
        thead.appendChild(headerRow)
        table.appendChild(thead)
        return table
    }

    private fun createTextCell(text: String, mono: Boolean): Element {
        val td = document.createElement("td") as HTMLElement
        td.textContent = text
        val font = if (mono) "font-family:'JetBrains Mono',monospace;" else ""
        td.style.cssText = "padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.03);opacity:0.8;${font}"
        return td
    }

    private fun createMethodCell(method: String): Element {
        val td = document.createElement("td") as HTMLElement
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = method.uppercase()
        val color = methodColor(method)
        badge.style.cssText = "font-size:10px;font-weight:700;padding:2px 8px;border-radius:4px;background:${color.first};color:${color.second};"
        td.style.cssText = "padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.03);"
        td.appendChild(badge)
        return td
    }

    private fun createOperationCell(op: String): Element {
        val td = document.createElement("td") as HTMLElement
        val badge = document.createElement("span") as HTMLElement
        badge.textContent = op.uppercase()
        val color = operationColor(op)
        badge.style.cssText = "font-size:10px;font-weight:700;padding:2px 8px;border-radius:4px;background:${color.first};color:${color.second};"
        td.style.cssText = "padding:8px 12px;border-bottom:1px solid rgba(255,255,255,0.03);"
        td.appendChild(badge)
        return td
    }

    private fun methodColor(m: String): Pair<String, String> = when (m.uppercase()) {
        "GET" -> Pair("rgba(45,254,207,0.12)", "var(--primary)")
        "POST" -> Pair("rgba(51,134,255,0.12)", "var(--accent)")
        "PUT", "PATCH" -> Pair("rgba(255,180,50,0.12)", "#ffb432")
        "DELETE" -> Pair("rgba(255,80,80,0.12)", "#ff5050")
        else -> Pair("rgba(255,255,255,0.06)", "var(--text-sub)")
    }

    private fun operationColor(op: String): Pair<String, String> = when (op.uppercase()) {
        "CREATE" -> Pair("rgba(45,254,207,0.12)", "var(--primary)")
        "ALTER" -> Pair("rgba(255,180,50,0.12)", "#ffb432")
        "DROP" -> Pair("rgba(255,80,80,0.12)", "#ff5050")
        else -> Pair("rgba(255,255,255,0.06)", "var(--text-sub)")
    }
}
