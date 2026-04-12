package com.assistant.frontend

import com.assistant.auth.UserRole
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.router.Router
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement

/**
 * Startup logic: auth check, Jira status, first-launch flow.
 * Requirements: 1.1, 1.2, 1.3
 */
internal object AppStartup {

    fun checkAuthAndNavigate() {
        val token = ApiClient.getToken()
        if (token == null) {
            Router.navigateTo("login")
            return
        }
        val projectKey = ApiClient.getProjectKey()
        if (projectKey.isNullOrBlank()) {
            Router.navigateTo("project_select")
        } else if (window.location.hash.isBlank() || window.location.hash == "#") {
            checkJiraStatusAndNavigate()
        } else {
            Router.handleRoute()
        }
    }

    private fun checkJiraStatusAndNavigate() {
        val scope = MainScope()
        scope.launch {
            try {
                val response = ApiClient.get("/api/integrations/jira/status")
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    val jsonObj = Json.parseToJsonElement(body).jsonObject
                    val configured = jsonObj["configured"]
                        ?.jsonPrimitive?.boolean ?: true
                    navigateBasedOnJiraStatus(configured)
                } else {
                    Router.navigateTo("dashboard")
                }
            } catch (e: Exception) {
                console.log("[App] Jira status check failed: ${e.message}")
                Router.navigateTo("dashboard")
            }
        }
    }

    private fun navigateBasedOnJiraStatus(configured: Boolean) {
        if (!configured) {
            val userRole = ApiClient.getUserRole()
            if (userRole == UserRole.ADMINISTRATOR) {
                Router.navigateTo("integrations")
            } else {
                showToast("Please ask an administrator to configure Jira")
                Router.navigateTo("dashboard")
            }
        } else {
            Router.navigateTo("dashboard")
        }
    }

    fun showToast(message: String, durationMs: Int = 5000) {
        val toast = document.createElement("div") as HTMLElement
        toast.className = "app-toast"
        toast.textContent = message
        applyToastStyles(toast)
        document.body?.appendChild(toast)
        window.setTimeout({ toast.style.opacity = "1" }, 50)
        window.setTimeout({
            toast.style.opacity = "0"
            window.setTimeout({ toast.remove() }, 400)
        }, durationMs)
    }

    private fun applyToastStyles(toast: HTMLElement) {
        toast.style.apply {
            position = "fixed"
            bottom = "24px"
            left = "50%"
            transform = "translateX(-50%)"
            background = "rgba(255, 180, 50, 0.95)"
            color = "#1a1a2e"
            padding = "12px 24px"
            borderRadius = "8px"
            fontSize = "14px"
            fontWeight = "500"
            zIndex = "10000"
            boxShadow = "0 4px 20px rgba(0,0,0,0.3)"
            transition = "opacity 0.4s ease"
            opacity = "0"
        }
    }
}
