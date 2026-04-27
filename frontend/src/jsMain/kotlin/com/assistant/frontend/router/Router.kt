package com.assistant.frontend.router

import kotlinx.browser.window
import org.w3c.dom.Element

/**
 * Hash-based SPA router for the Jira Assistant frontend.
 *
 * Routes are defined as hash fragments: #dashboard, #analysis, #knowledge_graph, etc.
 * Each route maps to a page renderer function that receives the content container element.
 *
 * All routes render inside the Shell (sidebar + navbar):
 *   dashboard, knowledge_graph, analysis, ticket_intelligence,
 *   integrations, user_management, settings
 */
object Router {

    /**
     * Represents a registered route with its renderer.
     */
    data class Route(
        val path: String,
        val render: (container: Element) -> Unit
    )

    private val routes = mutableMapOf<String, Route>()
    private val standaloneRoutes = mutableSetOf<String>()
    private var appContainer: Element? = null
    private var currentRoute: String? = null
    private var shellRenderer: ((container: Element, contentRenderer: (Element) -> Unit) -> Unit)? = null

    /**
     * Initialize the router with the root app container.
     * Sets up the hashchange event listener.
     */
    fun init(container: Element) {
        appContainer = container

        // Listen for hash changes
        window.addEventListener("hashchange", { event ->
            handleRoute()
        })
    }

    /**
     * Register a route with its page renderer.
     * @param path The hash path without '#' (e.g., "dashboard")
     * @param render Function that renders the page into the given container element
     */
    fun register(path: String, render: (container: Element) -> Unit) {
        routes[path] = Route(path, render)
    }

    /**
     * Register the app shell renderer.
     * The shell wraps page content with sidebar + navbar.
     * @param renderer Function that renders the shell, calling contentRenderer for the page content area
     */
    fun registerShell(renderer: (container: Element, contentRenderer: (Element) -> Unit) -> Unit) {
        shellRenderer = renderer
    }

    /**
     * Mark a route as standalone — it renders WITHOUT the Shell (no sidebar/navbar).
     * Used for login, onboarding, and other full-screen pages.
     */
    fun registerStandalone(path: String) {
        standaloneRoutes.add(path)
    }

    /**
     * Parse the current hash and render the matching route.
     * Standalone routes render directly; all others render inside the Shell.
     */
    fun handleRoute() {
        val container = appContainer ?: return
        val hash = window.location.hash.removePrefix("#").ifBlank { "dashboard" }

        // Avoid re-rendering the same route
        if (hash == currentRoute) return
        currentRoute = hash

        val route = routes[hash]
        val isStandalone = hash in standaloneRoutes

        if (isStandalone) {
            // Standalone route — clear everything and render without Shell
            container.innerHTML = ""
            if (route != null) {
                route.render(container)
            } else {
                renderNotFound(container, hash)
            }
        } else {
            // Normal route — render inside Shell (don't clear if shell exists)
            val shell = shellRenderer
            if (shell != null) {
                shell(container) { contentArea ->
                    if (route != null) {
                        route.render(contentArea)
                    } else {
                        renderNotFound(contentArea, hash)
                    }
                }
            } else {
                container.innerHTML = ""
                if (route != null) {
                    route.render(container)
                } else {
                    renderNotFound(container, hash)
                }
            }
        }

        // Update active nav state
        updateActiveNav(hash)

        // Update breadcrumb and project badge for non-standalone routes
        if (!isStandalone) {
            com.assistant.frontend.components.Navbar.updateBreadcrumb(hash)
            com.assistant.frontend.components.NavbarDropdown.refreshProjectSelector()
        }
    }

    /**
     * Navigate to a route programmatically.
     * If the hash is already the target, force a re-render.
     */
    fun navigateTo(path: String) {
        val target = "#$path"
        if (window.location.hash == target) {
            // Hash didn't change — force re-render
            currentRoute = null
            handleRoute()
        } else {
            window.location.hash = target
        }
    }

    /**
     * Get the current route path (without #).
     */
    fun getCurrentRoute(): String {
        return window.location.hash.removePrefix("#").ifBlank { "dashboard" }
    }

    // --- Internal Helpers ---

    private fun renderNotFound(container: Element, path: String) {
        container.innerHTML = """
            <div style="display:flex;align-items:center;justify-content:center;height:100%;flex-direction:column;gap:16px;">
                <h1 style="font-weight:100;font-size:48px;opacity:0.3;">404</h1>
                <p style="opacity:0.5;">Route <code>#$path</code> not found</p>
            </div>
        """.trimIndent()
    }

    private fun updateActiveNav(currentPath: String) {
        // Update sidebar nav items active state
        val navItems = kotlinx.browser.document.querySelectorAll(".nav-item")
        for (i in 0 until navItems.length) {
            val item = navItems.item(i) as? org.w3c.dom.HTMLElement ?: continue
            val href = item.getAttribute("data-route") ?: ""
            if (href == currentPath) {
                item.classList.add("active")
            } else {
                item.classList.remove("active")
            }
        }
    }
}
