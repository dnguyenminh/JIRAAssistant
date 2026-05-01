package com.assistant.server.routes

import com.assistant.ai.ProviderType
import com.assistant.jira.JiraCredentialsService
import com.assistant.kb.ProviderConfigRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class HealthResponse(
    val status: String,
    val jira: ComponentHealth,
    val aiProvider: ComponentHealth,
    val knowledgeBase: ComponentHealth
)

@Serializable
data class ComponentHealth(
    val status: String,
    val message: String? = null
)

/**
 * GET /health — returns connectivity status for Jira API, AI provider, and Knowledge Base.
 * No authentication required.
 */
fun Routing.healthRoutes() {
    val jiraCredentialsService by inject<JiraCredentialsService>()
    val providerConfigRepo by inject<ProviderConfigRepository>()
    val httpClient by inject<HttpClient>()

    get("/health") {
        val jiraHealth = checkJira(httpClient, jiraCredentialsService)
        val aiHealth = checkAiProvider(httpClient, providerConfigRepo)
        val kbHealth = checkKnowledgeBase()

        val overallStatus = if (
            jiraHealth.status == "up" &&
            aiHealth.status == "up" &&
            kbHealth.status == "up"
        ) "healthy" else "degraded"

        call.respond(HttpStatusCode.OK, HealthResponse(
            status = overallStatus,
            jira = jiraHealth,
            aiProvider = aiHealth,
            knowledgeBase = kbHealth
        ))
    }
}

private suspend fun checkJira(
    client: HttpClient, jiraCredentialsService: JiraCredentialsService
): ComponentHealth {
    val credentials = jiraCredentialsService.getJiraCredentials()
        ?: return ComponentHealth(status = "down", message = "Not configured")
    return try {
        val response = client.get("${credentials.domain}/rest/api/3/serverInfo")
        if (response.status.isSuccess()) {
            ComponentHealth(status = "up")
        } else {
            ComponentHealth(status = "down", message = "HTTP ${response.status.value}")
        }
    } catch (e: Exception) {
        ComponentHealth(status = "down", message = e.message ?: "Connection failed")
    }
}

private suspend fun checkAiProvider(
    client: HttpClient, providerConfigRepo: ProviderConfigRepository
): ComponentHealth {
    val aiTypes = listOf(ProviderType.OLLAMA, ProviderType.GEMINI, ProviderType.LM_STUDIO)
    val provider = aiTypes.firstNotNullOfOrNull { providerConfigRepo.findByType(it) }
        ?: return ComponentHealth(status = "down", message = "Not configured")
    val endpoint = provider.endpoint
    if (endpoint.isBlank()) {
        return ComponentHealth(status = "down", message = "Not configured")
    }
    return try {
        val response = client.get(endpoint)
        if (response.status.isSuccess()) {
            ComponentHealth(status = "up")
        } else {
            ComponentHealth(status = "down", message = "HTTP ${response.status.value}")
        }
    } catch (e: Exception) {
        ComponentHealth(status = "down", message = e.message ?: "Connection failed")
    }
}

private fun checkKnowledgeBase(): ComponentHealth {
    return try {
        val ds = org.koin.java.KoinJavaComponent.getKoin().get<javax.sql.DataSource>()
        ds.connection.use { conn ->
            conn.prepareStatement("SELECT 1").use { it.executeQuery() }
        }
        ComponentHealth(status = "up")
    } catch (e: Exception) {
        ComponentHealth(status = "down", message = e.message ?: "Database check failed")
    }
}
