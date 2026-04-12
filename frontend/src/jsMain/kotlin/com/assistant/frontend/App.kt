package com.assistant.frontend

import com.assistant.frontend.components.Shell
import com.assistant.frontend.pages.*
import com.assistant.frontend.router.Router
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/**
 * Frontend application entry point.
 * Initializes the router, registers routes, checks auth state.
 */
fun main() {
    window.onload = { initApp() }
}

private fun initApp() {
    val root = document.getElementById("root") ?: run {
        console.error("Root element #root not found"); return
    }
    root.innerHTML = ""

    val livingVoid = document.createElement("div")
    livingVoid.className = "living-void"
    root.appendChild(livingVoid)

    val appContainer = document.createElement("div")
    appContainer.id = "app-container"
    appContainer.className = "master-container"
    root.appendChild(appContainer)

    Router.init(appContainer)
    Router.registerShell { container, contentRenderer ->
        Shell.render(container, contentRenderer)
    }

    registerRoutes()
    AppStartup.checkAuthAndNavigate()
    console.log("[App] Jira Assistant Frontend initialized")
}

private fun registerRoutes() {
    Router.register("dashboard") { DashboardPage.render(it) }
    Router.register("knowledge_graph") { KnowledgeGraphPage.render(it) }
    Router.register("analysis") { AnalysisPage.render(it) }
    Router.register("ticket_intelligence") { TicketIntelligencePage.render(it) }
    Router.register("integrations") { IntegrationsPage.render(it) }
    Router.register("user_management") { UserManagementPage.render(it) }
    Router.register("settings") { SettingsPage.render(it) }

    Router.registerStandalone("login")
    Router.register("login") { LoginPage.render(it) }

    Router.registerStandalone("project_select")
    Router.register("project_select") { ProjectSelectPage.render(it) }
}
