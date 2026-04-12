package com.assistant.frontend.api

import com.assistant.auth.UserRole
import kotlinx.browser.window

/**
 * Manages JWT token and user session data in sessionStorage.
 */
internal object TokenManager {

    private const val TOKEN_KEY = "jira_assistant_jwt"
    private const val USER_ROLE_KEY = "jira_assistant_role"
    private const val USER_EMAIL_KEY = "jira_assistant_email"
    private const val PROJECT_KEY = "jira_assistant_project"

    fun saveToken(jwt: String) {
        window.sessionStorage.setItem(TOKEN_KEY, jwt)
    }

    fun getToken(): String? =
        window.sessionStorage.getItem(TOKEN_KEY)

    fun clearAll() {
        window.sessionStorage.removeItem(TOKEN_KEY)
        window.sessionStorage.removeItem(USER_ROLE_KEY)
        window.sessionStorage.removeItem(USER_EMAIL_KEY)
        window.sessionStorage.removeItem(PROJECT_KEY)
    }

    fun saveProjectKey(key: String) {
        window.sessionStorage.setItem(PROJECT_KEY, key)
    }

    fun getProjectKey(): String? =
        window.sessionStorage.getItem(PROJECT_KEY)

    fun clearProjectKey() {
        window.sessionStorage.removeItem(PROJECT_KEY)
    }

    fun saveUserInfo(role: UserRole, email: String) {
        window.sessionStorage.setItem(USER_ROLE_KEY, role.name)
        window.sessionStorage.setItem(USER_EMAIL_KEY, email)
    }

    fun getUserRole(): UserRole? {
        val roleName = window.sessionStorage.getItem(USER_ROLE_KEY)
            ?: return null
        return try { UserRole.valueOf(roleName) } catch (_: Exception) { null }
    }

    fun getUserEmail(): String? =
        window.sessionStorage.getItem(USER_EMAIL_KEY)
}
