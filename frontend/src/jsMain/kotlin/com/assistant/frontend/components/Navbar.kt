package com.assistant.frontend.components

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.router.Router
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Top navbar component with breadcrumb, project badge, user dropdown.
 * Follows Obsidian Kinetic design system.
 */
object Navbar {

    private val routeNames = mapOf(
        "dashboard" to "OVERVIEW",
        "knowledge_graph" to "RELATIONSHIP NETWORK",
        "analysis" to "PROJECT ANALYSIS",
        "ticket_intelligence" to "TICKET INTELLIGENCE",
        "integrations" to "INTEGRATIONS",
        "user_management" to "USER MANAGEMENT",
        "settings" to "APP SETTINGS"
    )

    private val routeSections = mapOf(
        "dashboard" to "DASHBOARD",
        "knowledge_graph" to "KNOWLEDGE GRAPH",
        "analysis" to "ANALYSIS",
        "ticket_intelligence" to "INTELLIGENCE",
        "integrations" to "INTEGRATIONS",
        "user_management" to "MANAGEMENT",
        "settings" to "APP SETTINGS"
    )

    fun render(container: Element) {
        val navbar = document.createElement("nav") as HTMLElement
        navbar.className = "master-navbar"

        val currentRoute = Router.getCurrentRoute()
        val sectionName = routeSections[currentRoute] ?: currentRoute.uppercase()
        val pageName = routeNames[currentRoute] ?: currentRoute.uppercase()

        val breadcrumb = document.createElement("div") as HTMLElement
        breadcrumb.className = "breadcrumb"
        breadcrumb.innerHTML = """$sectionName / <span class="current" id="navbar-current-page">$pageName</span>"""
        navbar.appendChild(breadcrumb)

        val navActions = document.createElement("div") as HTMLElement
        navActions.className = "nav-actions"

        // AI Chat toggle button — in navbar header (Req 19.4)
        val chatBtn = document.createElement("button") as HTMLElement
        chatBtn.id = "btn-toggle-chat"
        chatBtn.className = "nav-chat-btn"
        chatBtn.setAttribute("aria-label", "Toggle AI Chat Sidebar")
        chatBtn.textContent = "\uD83D\uDCAC" // 💬
        chatBtn.addEventListener("click", { it.stopPropagation(); AIChatSidebar.toggle() })
        navActions.appendChild(chatBtn)

        NavbarDropdown.renderProjectSelector(navActions)
        NavbarDropdown.renderUserWidget(navActions)

        navbar.appendChild(navActions)

        document.addEventListener("click", { _ ->
            NavbarDropdown.closeAll()
        })

        container.appendChild(navbar)
    }

    fun updateBreadcrumb(route: String) {
        val sectionName = routeSections[route] ?: route.uppercase()
        val pageName = routeNames[route] ?: route.uppercase()
        val breadcrumb = document.querySelector(".breadcrumb") as? HTMLElement
        breadcrumb?.innerHTML = """$sectionName / <span class="current" id="navbar-current-page">$pageName</span>"""
    }
}
