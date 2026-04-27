package com.assistant.frontend.components

import com.assistant.frontend.router.Router
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Navigation sidebar component with active state highlight,
 * status panel, and hash-based navigation links.
 *
 * Follows Obsidian Kinetic design system with glassmorphism styling.
 */
object Sidebar {

    data class NavItem(
        val route: String,
        val icon: String,
        val label: String
    )

    private val navItems = listOf(
        NavItem("dashboard", "🏠", "Dashboard"),
        NavItem("knowledge_graph", "🔍", "Relationship Network"),
        NavItem("analysis", "📊", "Project Analysis"),
        NavItem("ticket_intelligence", "✨", "Ticket Intelligence"),
        NavItem("integrations", "🔌", "Integrations"),
        NavItem("user_management", "👥", "User Management"),
        NavItem("settings", "⚙️", "Settings")
    )

    /**
     * Render the sidebar into the given container element.
     */
    fun render(container: Element) {
        val sidebar = document.createElement("aside") as HTMLElement
        sidebar.className = "master-sidebar"

        // Logo section
        val logoSection = document.createElement("div") as HTMLElement
        logoSection.className = "logo-section"
        logoSection.innerHTML = """
            <div class="logo-icon"></div>
            <span class="logo-title">JIRA ASSISTANT</span>
        """.trimIndent()
        sidebar.appendChild(logoSection)

        // Navigation
        val nav = document.createElement("nav") as HTMLElement
        nav.className = "side-nav"

        val currentRoute = Router.getCurrentRoute()

        for (item in navItems) {
            val link = document.createElement("a") as HTMLElement
            link.className = "nav-item"
            if (item.route == currentRoute) {
                link.classList.add("active")
            }
            link.setAttribute("href", "#${item.route}")
            link.setAttribute("data-route", item.route)
            link.innerHTML = """<span class="icon">${item.icon}</span> ${item.label}"""

            link.addEventListener("click", { e ->
                e.preventDefault()
                Router.navigateTo(item.route)
            })

            nav.appendChild(link)
        }
        sidebar.appendChild(nav)

        // Status panel
        val statusPanel = document.createElement("div") as HTMLElement
        statusPanel.className = "status-panel"
        statusPanel.innerHTML = """
            <div class="status-text" id="sidebar-status-label">System Status: ACTIVE</div>
            <div class="neural-loader"><div class="neural-progress" id="sidebar-progress" style="width: 100%;"></div></div>
            <div class="status-text" id="sidebar-status-detail" style="opacity: 0.4;">READY</div>
        """.trimIndent()
        sidebar.appendChild(statusPanel)

        container.appendChild(sidebar)
    }

    /**
     * Update the active state of navigation items based on the current route.
     */
    fun updateActiveState(currentRoute: String) {
        val navItemElements = document.querySelectorAll(".nav-item")
        for (i in 0 until navItemElements.length) {
            val item = navItemElements.item(i) as? HTMLElement ?: continue
            val route = item.getAttribute("data-route") ?: ""
            if (route == currentRoute) {
                item.classList.add("active")
            } else {
                item.classList.remove("active")
            }
        }
    }

    /**
     * Update the status panel text and progress.
     */
    fun updateStatus(label: String, detail: String, progressPercent: Int) {
        (document.getElementById("sidebar-status-label") as? HTMLElement)?.textContent = label
        (document.getElementById("sidebar-status-detail") as? HTMLElement)?.textContent = detail
        (document.getElementById("sidebar-progress") as? HTMLElement)?.style?.width = "${progressPercent}%"
    }
}
