package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.router.Router
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Standalone login page — renders WITHOUT the Shell (no sidebar/navbar).
 * Obsidian Kinetic design: glass-card centered on living-void background.
 */
object LoginPage {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(container: Element) {
        container.innerHTML = ""
        scope.launch {
            val html = ApiClient.loadTemplate("login")
            container.innerHTML = html
            bindEvents()
        }
    }

    private fun bindEvents() {
        val usernameInput = document.getElementById("login-username") as? HTMLInputElement
        val passwordInput = document.getElementById("login-password") as? HTMLInputElement
        val loginBtn = document.getElementById("login-btn") as? HTMLButtonElement
        val errorDiv = document.getElementById("login-error") as? HTMLElement

        loginBtn?.addEventListener("click", {
            doLogin(usernameInput, passwordInput, loginBtn, errorDiv)
        })

        // Enter key submits
        val enterHandler: (org.w3c.dom.events.Event) -> Unit = { e ->
            val ke = e.unsafeCast<org.w3c.dom.events.KeyboardEvent>()
            if (ke.key == "Enter") {
                doLogin(usernameInput, passwordInput, loginBtn, errorDiv)
            }
        }
        usernameInput?.addEventListener("keydown", enterHandler)
        passwordInput?.addEventListener("keydown", enterHandler)

        // Auto-focus username
        usernameInput?.focus()
    }

    private fun doLogin(
        usernameInput: HTMLInputElement?,
        passwordInput: HTMLInputElement?,
        loginBtn: HTMLButtonElement?,
        errorDiv: HTMLElement?
    ) {
        val username = usernameInput?.value?.trim() ?: ""
        val password = passwordInput?.value ?: ""

        if (username.isBlank() || password.isBlank()) {
            showError(errorDiv, "Please enter username and password")
            return
        }

        // Disable button during request
        loginBtn?.disabled = true
        loginBtn?.textContent = "AUTHENTICATING..."
        hideError(errorDiv)

        BlockingOverlay.show("login-card", "Signing in...")
        scope.launch {
            try {
                val response = ApiClient.postUnauthenticated(
                    "/api/auth/login",
                    mapOf("email" to username, "password" to password)
                )

                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    val jsonObj = json.parseToJsonElement(body).jsonObject
                    val jwt = jsonObj["jwt"]?.jsonPrimitive?.content ?: ""
                    val userObj = jsonObj["user"]?.jsonObject
                    val role = userObj?.get("role")?.jsonPrimitive?.content ?: ""
                    val email = userObj?.get("email")?.jsonPrimitive?.content ?: ""

                    if (jwt.isNotBlank()) {
                        ApiClient.saveToken(jwt)
                        val userRole = try {
                            com.assistant.auth.UserRole.valueOf(role)
                        } catch (_: Exception) {
                            com.assistant.auth.UserRole.READER
                        }
                        ApiClient.saveUserInfo(userRole, email)

                        // Check if a project is already selected
                        val projectKey = ApiClient.getProjectKey()
                        if (projectKey.isNullOrBlank()) {
                            Router.navigateTo("project_select")
                        } else {
                            Router.navigateTo("dashboard")
                        }
                    } else {
                        showError(errorDiv, "Invalid response from server")
                        resetButton(loginBtn)
                    }
                } else {
                    val body = response.bodyAsText()
                    val msg = try {
                        val errObj = json.parseToJsonElement(body).jsonObject
                        errObj["error"]?.jsonPrimitive?.content ?: "Authentication failed"
                    } catch (_: Exception) {
                        "Invalid username or password"
                    }
                    showError(errorDiv, msg)
                    resetButton(loginBtn)
                }
            } catch (e: Exception) {
                showError(errorDiv, "Connection error: ${e.message}")
                resetButton(loginBtn)
            } finally {
                BlockingOverlay.remove("login-card")
            }
        }
    }

    private fun showError(errorDiv: HTMLElement?, message: String) {
        errorDiv?.textContent = message
        errorDiv?.style?.display = "block"
    }

    private fun hideError(errorDiv: HTMLElement?) {
        errorDiv?.style?.display = "none"
    }

    private fun resetButton(btn: HTMLButtonElement?) {
        btn?.disabled = false
        btn?.textContent = "SIGN IN"
    }
}
