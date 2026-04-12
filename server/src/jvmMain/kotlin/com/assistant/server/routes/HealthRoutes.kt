package com.assistant.server.routes

import com.assistant.server.config.ServerConfig
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
    val config by inject<ServerConfig>()
    val httpClient by inject<HttpClient>()

    get("/health") {
        val jiraHealth = checkJira(httpClient, config.jiraHost)
        val aiHealth = checkAiProvider(httpClient, config.aiProviderUrl)
        val kbHealth = checkKnowledgeBase(config.dbPath)

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

private suspend fun checkJira(client: HttpClient, jiraHost: String): ComponentHealth {
    return try {
        val response = client.get("$jiraHost/rest/api/3/serverInfo")
        if (response.status.isSuccess()) {
            ComponentHealth(status = "up")
        } else {
            ComponentHealth(status = "down", message = "HTTP ${response.status.value}")
        }
    } catch (e: Exception) {
        ComponentHealth(status = "down", message = e.message ?: "Connection failed")
    }
}

private suspend fun checkAiProvider(client: HttpClient, aiProviderUrl: String): ComponentHealth {
    return try {
        val response = client.get(aiProviderUrl)
        if (response.status.isSuccess()) {
            ComponentHealth(status = "up")
        } else {
            ComponentHealth(status = "down", message = "HTTP ${response.status.value}")
        }
    } catch (e: Exception) {
        ComponentHealth(status = "down", message = e.message ?: "Connection failed")
    }
}

private fun checkKnowledgeBase(dbPath: String): ComponentHealth {
    return try {
        val dbFile = java.io.File(dbPath)
        if (dbFile.exists() || dbFile.parentFile?.exists() == true || dbFile.parentFile?.mkdirs() == true) {
            ComponentHealth(status = "up")
        } else {
            ComponentHealth(status = "down", message = "Database path not accessible")
        }
    } catch (e: Exception) {
        ComponentHealth(status = "down", message = e.message ?: "Check failed")
    }
}
