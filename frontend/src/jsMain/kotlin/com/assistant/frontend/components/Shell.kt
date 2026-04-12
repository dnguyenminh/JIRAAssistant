package com.assistant.frontend.components

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import kotlin.js.Promise

/**
 * App shell component that provides the main layout structure:
 * Sidebar (left) + Main content area (Navbar top + workspace below).
 *
 * All pages except Onboarding render inside this shell.
 * Register with Router.registerShell() to wrap page content automatically.
 *
 * Login and Project Select pages are standalone (don't use Shell),
 * so the AI Chat Sidebar and toggle button are automatically hidden on those pages. (Req 19.4)
 */
object Shell {

    private val scope = MainScope()
    private var sidebarInitialized = false

    /**
     * Render the shell layout into the given container,
     * then invoke contentRenderer with the workspace element
     * so the active page can render its content inside.
     *
     * Layout structure:
     * ┌──────────┬──────────────────────┐
     * │          │  Navbar              │
     * │ Sidebar  ├──────────────────────┤
     * │          │  Workspace (content) │
     * │          │                      │
     * └──────────┴──────────────────────┘
     */
    fun render(container: Element, contentRenderer: (Element) -> Unit) {
        // Check if shell already exists — only re-render workspace content
        val existingWorkspace = document.getElementById("shell-workspace")
        if (existingWorkspace != null) {
            existingWorkspace.innerHTML = ""
            contentRenderer(existingWorkspace)
            return
        }

        // First render or coming from standalone: clear container and build full shell
        container.innerHTML = ""
        sidebarInitialized = false

        Sidebar.render(container)

        val rightArea = document.createElement("div") as HTMLElement
        rightArea.className = "shell-right-area"
        rightArea.id = "shell-right-area"

        val mainContent = document.createElement("div") as HTMLElement
        mainContent.className = "master-content"

        Navbar.render(mainContent)

        val workspace = document.createElement("div") as HTMLElement
        workspace.className = "workspace"
        workspace.id = "shell-workspace"
        mainContent.appendChild(workspace)

        rightArea.appendChild(mainContent)

        val chatContainer = document.createElement("div") as HTMLElement
        chatContainer.id = "ai-chat-sidebar-container"
        rightArea.appendChild(chatContainer)

        container.appendChild(rightArea)

        contentRenderer(workspace)

        loadChatSidebar(chatContainer)
    }

    /**
     * Fetch the AI Chat Sidebar HTML template, inject it into the container,
     * initialize the AIChatSidebar controller, and wire up the toggle button.
     * (Req 19.1, 19.4)
     */
    private fun loadChatSidebar(container: HTMLElement) {
        if (sidebarInitialized) return

        scope.launch {
            try {
                val response = window.fetch("/templates/ai-chat-sidebar.html")
                    .unsafeCast<Promise<org.w3c.fetch.Response>>()
                    .await()

                if (response.ok) {
                    val html = response.text().await()
                    container.innerHTML = html
                    // Inject resize handle at the left edge of the sidebar
                    val sidebar = document.getElementById("ai-chat-sidebar") as? HTMLElement
                    if (sidebar != null) {
                        val handle = document.createElement("div") as HTMLElement
                        handle.className = "chat-resize-handle"
                        sidebar.insertBefore(handle, sidebar.firstChild)
                        setupResize(handle, sidebar)
                    }
                    AIChatSidebar.init()
                    sidebarInitialized = true
                } else {
                    console.log("[Shell] Failed to load chat template: ${response.status}")
                }
            } catch (e: Exception) {
                console.log("[Shell] Error loading chat: ${e.message}")
            }
        }
    }

    private fun setupResize(handle: HTMLElement, sidebar: HTMLElement) {
        var dragging = false
        var startX = 0.0
        var startWidth = 0.0

        handle.addEventListener("mousedown", { e ->
            val me = e.asDynamic()
            dragging = true
            startX = me.clientX as Double
            startWidth = sidebar.offsetWidth.toDouble()
            handle.classList.add("dragging")
            e.preventDefault()
        })

        window.addEventListener("mousemove", { e ->
            if (!dragging) return@addEventListener
            val me = e.asDynamic()
            val dx = startX - (me.clientX as Double)
            val newWidth = (startWidth + dx).coerceIn(280.0, 600.0)
            sidebar.style.width = "${newWidth}px"
        })

        window.addEventListener("mouseup", {
            if (dragging) {
                dragging = false
                handle.classList.remove("dragging")
            }
        })
    }
}
