package com.assistant.frontend.api

import com.assistant.auth.AuthResult
import com.assistant.auth.UserRole
import com.assistant.rbac.Permission
import com.assistant.rbac.PermissionMatrix
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json

/**
 * HTTP API client (Facade pattern) wrapping ktor-client-js.
 * Delegates token management to TokenManager.
 */
object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(this@ApiClient.json) }
    }

    // ── Token delegation ────────────────────────────────────────

    fun saveToken(jwt: String) = TokenManager.saveToken(jwt)
    fun getToken(): String? = TokenManager.getToken()
    fun clearToken() = TokenManager.clearAll()
    fun saveProjectKey(key: String) = TokenManager.saveProjectKey(key)
    fun getProjectKey(): String? = TokenManager.getProjectKey()
    fun clearProjectKey() = TokenManager.clearProjectKey()
    fun saveUserInfo(role: UserRole, email: String) =
        TokenManager.saveUserInfo(role, email)
    fun getUserRole(): UserRole? = TokenManager.getUserRole()
    fun getUserEmail(): String? = TokenManager.getUserEmail()

    // ── RBAC ────────────────────────────────────────────────────

    fun hasPermission(permission: Permission): Boolean {
        val role = getUserRole() ?: return false
        return PermissionMatrix.getPermissions(role).contains(permission)
    }

    // ── HTTP Methods ────────────────────────────────────────────

    suspend fun get(path: String): HttpResponse =
        client.get(path) { applyAuth() }

    suspend fun post(path: String, body: Any? = null): HttpResponse =
        client.post(path) {
            applyAuth()
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }

    suspend fun put(path: String, body: Any? = null): HttpResponse =
        client.put(path) {
            applyAuth()
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }

    suspend fun delete(path: String): HttpResponse =
        client.delete(path) { applyAuth() }

    suspend fun postUnauthenticated(path: String, body: Any? = null): HttpResponse =
        client.post(path) {
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }

    // ── Auth Lifecycle ──────────────────────────────────────────

    fun onLoginSuccess(result: AuthResult.Success) {
        saveToken(result.jwt)
        saveUserInfo(result.user.role, result.user.email)
    }

    fun signOut() { clearToken(); window.location.hash = "#login" }

    fun handleUnauthorized(response: HttpResponse): Boolean {
        if (response.status == HttpStatusCode.Unauthorized) {
            clearToken(); return true
        }
        return false
    }

    // ── Template Loading ────────────────────────────────────────

    suspend fun loadTemplate(name: String): String {
        val response = window.fetch("/templates/$name.html")
            .unsafeCast<kotlin.js.Promise<org.w3c.fetch.Response>>()
            .await()
        return response.text().await()
    }

    private fun HttpRequestBuilder.applyAuth() {
        val token = getToken()
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
    }
}
