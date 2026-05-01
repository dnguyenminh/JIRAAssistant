package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.CreateUserRequest
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Create User form component — clones tmpl-create-form template,
 * validates input, POSTs to /api/users, and calls onSuccess callback.
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7, 1.10, 1.11
 */
object UserCreateForm {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true }
    private var formElement: HTMLElement? = null
    private var onSuccessCallback: (() -> Unit)? = null

    private const val CONTAINER_ID = "um-main-content"
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun show(container: HTMLElement, onSuccess: () -> Unit) {
        hide()
        onSuccessCallback = onSuccess
        val tmpl = document.getElementById("tmpl-create-form") as? HTMLTemplateElement ?: return
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return
        formElement = el
        container.insertBefore(el, container.firstChild)
        bindFormEvents(el)
    }

    fun hide() {
        formElement?.remove()
        formElement = null
    }

    private fun bindFormEvents(el: HTMLElement) {
        val submitBtn = el.querySelector("[data-action='submit']") as? HTMLElement
        val cancelBtn = el.querySelector("[data-action='cancel']") as? HTMLElement
        submitBtn?.addEventListener("click", { handleSubmit(el) })
        cancelBtn?.addEventListener("click", { hide() })
    }

    private fun handleSubmit(el: HTMLElement) {
        clearErrors(el)
        val name = getInputValue(el, "name")
        val email = getInputValue(el, "email")
        val role = getSelectValue(el, "role")
        if (!validateForm(el, name, email)) return
        submitCreateRequest(name, email, role)
    }

    private fun validateForm(el: HTMLElement, name: String, email: String): Boolean {
        var valid = true
        if (name.isBlank()) {
            showFieldError(el, "name", "Name is required"); valid = false
        }
        if (!EMAIL_REGEX.matches(email)) {
            showFieldError(el, "email", "Invalid email format"); valid = false
        }
        return valid
    }

    private fun submitCreateRequest(name: String, email: String, role: String) {
        BlockingOverlay.show(CONTAINER_ID, "Creating user...")
        scope.launch {
            try {
                val request = CreateUserRequest(name = name, email = email, role = role)
                val response = ApiClient.post("/api/users", request)
                handleResponse(response)
            } catch (e: Exception) {
                showGlobalError("Connection failed. Please try again.")
            } finally {
                BlockingOverlay.remove(CONTAINER_ID)
            }
        }
    }

    private fun handleResponse(response: HttpResponse) {
        if (response.status == HttpStatusCode.Created) {
            hide()
            onSuccessCallback?.invoke()
        } else {
            scope.launch { showApiError(response) }
        }
    }

    private suspend fun showApiError(response: HttpResponse) {
        val body = response.bodyAsText()
        val msg = try {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
        showGlobalError(msg ?: "Failed to create user (${response.status.value})")
    }

    private fun showGlobalError(message: String) {
        val el = formElement ?: return
        val errorEl = el.querySelector("[data-error='global']") as? HTMLElement ?: return
        errorEl.textContent = message
    }

    private fun showFieldError(el: HTMLElement, field: String, message: String) {
        val errorEl = el.querySelector("[data-error='$field']") as? HTMLElement ?: return
        errorEl.textContent = message
    }

    private fun clearErrors(el: HTMLElement) {
        el.querySelector("[data-error='name']")?.let { (it as HTMLElement).textContent = "" }
        el.querySelector("[data-error='email']")?.let { (it as HTMLElement).textContent = "" }
        el.querySelector("[data-error='global']")?.let { (it as HTMLElement).textContent = "" }
    }

    private fun getInputValue(el: HTMLElement, field: String): String {
        val input = el.querySelector("[data-field='$field']") as? HTMLInputElement
        return input?.value?.trim() ?: ""
    }

    private fun getSelectValue(el: HTMLElement, field: String): String {
        val select = el.querySelector("[data-field='$field']") as? HTMLSelectElement
        return select?.value ?: "READER"
    }
}
