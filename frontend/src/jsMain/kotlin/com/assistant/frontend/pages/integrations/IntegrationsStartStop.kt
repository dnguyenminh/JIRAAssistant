package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.ProviderInfo
import com.assistant.frontend.pages.IntegrationsPage
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * START/STOP toggle for AI provider cards.
 * START = test connection → set ACTIVE/OFFLINE.
 * STOP  = set status to OFFLINE (disable in failover).
 */
object IntegrationsStartStop {

    private val scope = MainScope()

    fun toggle(provider: ProviderInfo, card: HTMLElement) {
        val isActive = provider.status.uppercase() == "ACTIVE"
        if (isActive) stop(provider, card) else start(provider, card)
    }

    private fun start(provider: ProviderInfo, card: HTMLElement) {
        val cardId = "integ-card-${provider.providerId}"
        BlockingOverlay.show(cardId, "Starting...")
        scope.launch {
            try {
                val resp = ApiClient.post(
                    "/api/integrations/${provider.providerId}/test",
                    mapOf<String, String>()
                )
                val body = resp.bodyAsText()
                val success = body.contains("\"success\":true")
                val newStatus = if (success) "ACTIVE" else "OFFLINE"
                updateProviderStatus(provider.providerId, newStatus)
                if (success) {
                    ToastService.show("✓ ${provider.name} started", "success")
                } else {
                    ToastService.show("✗ ${provider.name}: connection failed", "error")
                }
                IntegrationsPage.renderProviderCards()
            } catch (e: Exception) {
                ToastService.show("✗ ${provider.name}: ${e.message}", "error")
            } finally {
                BlockingOverlay.remove(cardId)
            }
        }
    }

    private fun stop(provider: ProviderInfo, card: HTMLElement) {
        val cardId = "integ-card-${provider.providerId}"
        BlockingOverlay.show(cardId, "Stopping...")
        scope.launch {
            try {
                ApiClient.put(
                    "/api/integrations/${provider.providerId}/status",
                    mapOf("status" to "OFFLINE")
                )
                updateProviderStatus(provider.providerId, "OFFLINE")
                ToastService.show("✓ ${provider.name} stopped", "success")
                IntegrationsPage.renderProviderCards()
            } catch (e: Exception) {
                ToastService.show("✗ ${provider.name}: ${e.message}", "error")
            } finally {
                BlockingOverlay.remove(cardId)
            }
        }
    }

    private fun updateProviderStatus(providerId: String, status: String) {
        val idx = IntegrationsPage.providers.indexOfFirst {
            it.providerId == providerId
        }
        if (idx >= 0) {
            IntegrationsPage.providers[idx] =
                IntegrationsPage.providers[idx].copy(status = status)
        }
    }
}
