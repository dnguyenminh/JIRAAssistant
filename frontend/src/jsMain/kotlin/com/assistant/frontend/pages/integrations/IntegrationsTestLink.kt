package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.TestResult
import com.assistant.frontend.pages.IntegrationsPage
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * TEST LINK button logic with progress bar and status update.
 */
internal object IntegrationsTestLink {

    fun testProviderLink(providerId: String) {
        val card = document.getElementById("integ-card-$providerId") ?: return
        val btn = card.querySelector(".integ-btn-test") as? HTMLElement ?: return
        val progressContainer = card.querySelector(".integ-test-progress[data-provider='$providerId']") as? HTMLElement ?: return
        val bar = progressContainer.querySelector(".integ-test-bar") as? HTMLElement ?: return

        val originalText = btn.textContent ?: "TEST LINK"
        btn.textContent = "PROBING..."; btn.style.opacity = "0.6"
        btn.style.asDynamic().pointerEvents = "none"
        progressContainer.style.display = "block"; bar.style.width = "0%"

        var progress = 0
        val intervalId = window.setInterval({
            progress += 5; bar.style.width = "${minOf(progress, 90)}%"
        }, 80)

        val cardId = "integ-card-$providerId"
        BlockingOverlay.show(cardId, "Testing...")
        IntegrationsPage.scope.launch {
            try {
                val response = ApiClient.post("/api/integrations/$providerId/test")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val result = IntegrationsPage.json.decodeFromString<TestResult>(body)
                window.clearInterval(intervalId); bar.style.width = "100%"
                delay(400)
                val effectiveStatus = if (result.success) "ACTIVE" else "OFFLINE"
                IntegrationsPage.updateCardStatus(providerId, effectiveStatus, result.latencyMs)
                if (result.success) ToastService.show(result.message.ifBlank { "$providerId: Connected" }, "success")
                else ToastService.show(result.message.ifBlank { "$providerId: Connection failed" }, "error")
                updateLocalProvider(providerId, result)
            } catch (e: Exception) {
                window.clearInterval(intervalId); bar.style.width = "100%"
                ToastService.show("Test failed: ${e.message ?: "Connection error"}", "error")
            } finally {
                delay(300)
                btn.textContent = originalText; btn.style.opacity = "1"
                btn.style.asDynamic().pointerEvents = "auto"
                progressContainer.style.display = "none"; bar.style.width = "0%"
                BlockingOverlay.remove(cardId)
            }
        }
    }

    private fun updateLocalProvider(providerId: String, result: TestResult) {
        val idx = IntegrationsPage.providers.indexOfFirst { it.providerId == providerId }
        if (idx >= 0) {
            IntegrationsPage.providers[idx] = IntegrationsPage.providers[idx].copy(
                status = result.status, latencyMs = result.latencyMs,
                errorMessage = if (!result.success) result.message else null
            )
        }
    }
}
