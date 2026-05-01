package com.assistant.frontend.pages

import com.assistant.frontend.router.Router
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * UI state helpers for ProjectSelectPage — credential error,
 * Jira-not-configured popup/footer, and empty-with-retry states.
 */
object ProjectSelectStates {

    /** Clone credential error template into table body with Integrations link. */
    fun showCredentialError() {
        val tbody = document.getElementById("project-table-body")
            as? HTMLElement ?: return
        tbody.innerHTML = ""
        val tmpl = document.getElementById("tmpl-credential-error")
            as? HTMLTemplateElement ?: return
        val content = tmpl.content.firstElementChild
            ?.cloneNode(true) as? HTMLElement ?: return
        content.querySelector("#btn-go-integrations")
            ?.addEventListener("click", { Router.navigateTo("integrations") })
        val tr = document.createElement("tr") as HTMLElement
        val td = document.createElement("td") as HTMLElement
        td.setAttribute("colspan", "3")
        td.appendChild(content)
        tr.appendChild(td)
        tbody.appendChild(tr)
    }

    /** Non-admin: show disconnect footer + popup overlay. */
    fun showJiraNotConfigured() {
        cloneAndAppendFooter()
        cloneAndShowPopup()
    }

    private fun cloneAndAppendFooter() {
        val tmpl = document.getElementById("tmpl-jira-disconnect")
            as? HTMLTemplateElement ?: return
        val footer = tmpl.content.firstElementChild
            ?.cloneNode(true) as? HTMLElement ?: return
        document.body?.appendChild(footer)
    }

    private fun cloneAndShowPopup() {
        val tmpl = document.getElementById("tmpl-jira-not-configured-popup")
            as? HTMLTemplateElement ?: return
        val overlay = tmpl.content.firstElementChild
            ?.cloneNode(true) as? HTMLElement ?: return
        document.body?.appendChild(overlay)
        overlay.querySelector("#jira-popup-ok-btn")
            ?.addEventListener("click", { overlay.remove() })
    }

    /** Empty state with RETRY button. */
    fun showEmptyWithRetry(onRetry: () -> Unit) {
        val tbody = document.getElementById("project-table-body")
            as? HTMLElement ?: return
        tbody.innerHTML = ""
        val tr = document.createElement("tr") as HTMLElement
        val td = document.createElement("td") as HTMLElement
        td.setAttribute("colspan", "3")
        td.style.cssText = "text-align:center;padding:48px;"
        val msg = document.createElement("span") as HTMLElement
        msg.style.opacity = "0.5"
        msg.textContent = "No projects available."
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn"
        btn.style.cssText = "margin-top:16px;padding:8px 24px;"
        btn.textContent = "RETRY"
        btn.addEventListener("click", { onRetry() })
        td.appendChild(msg)
        td.appendChild(document.createElement("br"))
        td.appendChild(btn)
        tr.appendChild(td)
        tbody.appendChild(tr)
    }
}
