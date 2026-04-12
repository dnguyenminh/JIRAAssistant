package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.McpServerInfo
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Atlassian Rovo MCP Server preset config modal.
 * Pre-fills URL https://mcp.atlassian.com/v1/mcp,
 * supports OAuth 2.1 or API token authentication.
 * Requirements: 17.1
 */
object McpAtlassianPreset {

    private val scope = MainScope()
    private const val MCP_URL = "https://mcp.atlassian.com/v1/mcp"
    private var authMode = "oauth" // "oauth" | "token"

    fun open() {
        val overlay = el("atlassian-rovo-modal") ?: return
        overlay.style.display = "flex"
        authMode = "oauth"
        clearForm()
        updateAuthToggle()
        bindEvents()
        loadExistingConfig()
    }

    /** Load existing atlassian-rovo config from server and populate form. */
    private fun loadExistingConfig() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/integrations/mcp")
                if (resp.status != HttpStatusCode.OK) return@launch
                val body = resp.bodyAsText()
                val servers = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<McpServerInfo>>(body)
                val existing = servers.find { it.name == "atlassian-rovo" } ?: return@launch
                populateFormFromConfig(existing)
            } catch (_: Exception) { /* ignore — just show empty form */ }
        }
    }

    private fun populateFormFromConfig(config: McpServerInfo) {
        val env = try {
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(config.env)
        } catch (_: Exception) { emptyMap() }
        val type = env["MCP_AUTH_TYPE"] ?: "token"
        authMode = type
        updateAuthToggle()
        if (type == "oauth") {
            setInput("atlassian-client-id", env["OAUTH_CLIENT_ID"] ?: "")
            setInput("atlassian-client-secret", env["OAUTH_CLIENT_SECRET"] ?: "")
            setInput("atlassian-scopes", env["OAUTH_SCOPES"] ?: "read:jira-work write:jira-work read:confluence-content.all")
        } else {
            setInput("atlassian-site-url", env["ATLASSIAN_SITE_URL"] ?: "")
            setInput("atlassian-email", env["ATLASSIAN_EMAIL"] ?: "")
            setInput("atlassian-api-token", env["ATLASSIAN_API_TOKEN"] ?: "")
        }
    }

    fun close() {
        el("atlassian-rovo-modal")?.style?.display = "none"
        clearForm()
    }

    private fun bindEvents() {
        el("atlassian-rovo-close")?.onclick = { close() }
        el("atlassian-rovo-modal")?.onclick = { e ->
            if ((e.target as? HTMLElement)?.id == "atlassian-rovo-modal") close()
        }
        el("atlassian-auth-oauth")?.onclick = { switchAuth("oauth") }
        el("atlassian-auth-token")?.onclick = { switchAuth("token") }
        el("btn-atlassian-rovo-save")?.onclick = { save() }
        bindToggleVisibility("atlassian-toggle-secret", "atlassian-client-secret")
        bindToggleVisibility("atlassian-toggle-token", "atlassian-api-token")
    }

    private fun switchAuth(mode: String) {
        authMode = mode
        updateAuthToggle()
    }

    private fun updateAuthToggle() {
        val oauthBtn = el("atlassian-auth-oauth")
        val tokenBtn = el("atlassian-auth-token")
        val oauthFields = el("atlassian-oauth-fields")
        val tokenFields = el("atlassian-token-fields")
        if (authMode == "oauth") {
            oauthBtn?.classList?.add("active")
            tokenBtn?.classList?.remove("active")
            oauthFields?.style?.display = ""
            tokenFields?.style?.display = "none"
        } else {
            oauthBtn?.classList?.remove("active")
            tokenBtn?.classList?.add("active")
            oauthFields?.style?.display = "none"
            tokenFields?.style?.display = ""
        }
    }

    private fun save() {
        val env = buildEnvString()
        if (env == null) return
        val autoApprove = input("atlassian-auto-approve")
        val config = McpServerInfo(
            name = "atlassian-rovo",
            type = "streamable-http",
            url = MCP_URL,
            env = env,
            autoApprove = if (autoApprove.isNotBlank()) {
                "[${autoApprove.split(",").joinToString(",") { "\"${it.trim()}\"" }}]"
            } else "[]"
        )
        BlockingOverlay.show("atlassian-rovo-modal-content", "Saving...")
        scope.launch {
            try {
                val resp = trySaveOrUpdate(config)
                if (resp.status == HttpStatusCode.OK || resp.status == HttpStatusCode.Created) {
                    close()
                    McpServerCards.load()
                    ToastService.show("✓ Atlassian Rovo MCP added", "success")
                } else {
                    val body = resp.bodyAsText()
                    showStatus("Save failed (${resp.status.value}): $body", true)
                }
            } catch (e: Exception) {
                showStatus("Save failed: ${e.message}", true)
            } finally {
                BlockingOverlay.remove("atlassian-rovo-modal-content")
            }
        }
    }

    /** Try POST first; if 409 Conflict (duplicate), find existing and PUT update. */
    private suspend fun trySaveOrUpdate(config: McpServerInfo): io.ktor.client.statement.HttpResponse {
        val resp = ApiClient.post("/api/integrations/mcp", config)
        if (resp.status == HttpStatusCode.Conflict) {
            val existing = findExistingId()
            if (existing != null) {
                return ApiClient.put("/api/integrations/mcp/$existing", config)
            }
        }
        return resp
    }

    private suspend fun findExistingId(): String? {
        return try {
            val resp = ApiClient.get("/api/integrations/mcp")
            val body = resp.bodyAsText()
            val servers = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<McpServerInfo>>(body)
            servers.find { it.name == "atlassian-rovo" }?.id
        } catch (_: Exception) { null }
    }

    private fun buildEnvString(): String? {
        val pairs = mutableMapOf<String, String>()
        pairs["MCP_AUTH_TYPE"] = authMode
        if (authMode == "oauth") {
            val clientId = input("atlassian-client-id")
            val clientSecret = input("atlassian-client-secret")
            if (clientId.isBlank() || clientSecret.isBlank()) {
                showStatus("Client ID and Client Secret are required", true)
                return null
            }
            pairs["OAUTH_CLIENT_ID"] = clientId
            pairs["OAUTH_CLIENT_SECRET"] = clientSecret
            pairs["OAUTH_SCOPES"] = input("atlassian-scopes")
        } else {
            val siteUrl = input("atlassian-site-url")
            val email = input("atlassian-email")
            val token = input("atlassian-api-token")
            if (siteUrl.isBlank() || email.isBlank() || token.isBlank()) {
                showStatus("Site URL, Email, and API Token are required", true)
                return null
            }
            pairs["ATLASSIAN_SITE_URL"] = siteUrl
            pairs["ATLASSIAN_EMAIL"] = email
            pairs["ATLASSIAN_API_TOKEN"] = token
        }
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                for ((k, v) in pairs) put(k, kotlinx.serialization.json.JsonPrimitive(v))
            }
        )
    }

    private fun clearForm() {
        listOf(
            "atlassian-client-id", "atlassian-client-secret",
            "atlassian-site-url", "atlassian-email", "atlassian-api-token",
            "atlassian-auto-approve"
        ).forEach { setInput(it, "") }
        setInput("atlassian-scopes", "read:jira-work write:jira-work read:confluence-content.all")
        hideStatus()
    }

    private fun showStatus(msg: String, isError: Boolean) {
        val s = el("atlassian-rovo-status") ?: return
        s.style.display = "block"
        s.textContent = msg
        s.style.color = if (isError) "var(--danger)" else "var(--primary)"
    }

    private fun hideStatus() {
        el("atlassian-rovo-status")?.style?.display = "none"
    }

    private fun bindToggleVisibility(btnId: String, inputId: String) {
        el(btnId)?.onclick = {
            val inp = document.getElementById(inputId) as? HTMLInputElement
            inp?.type = if (inp?.type == "password") "text" else "password"
        }
    }

    private fun el(id: String) = document.getElementById(id) as? HTMLElement
    private fun input(id: String) = (document.getElementById(id) as? HTMLInputElement)?.value ?: ""
    private fun setInput(id: String, v: String) { (document.getElementById(id) as? HTMLInputElement)?.value = v }
}
