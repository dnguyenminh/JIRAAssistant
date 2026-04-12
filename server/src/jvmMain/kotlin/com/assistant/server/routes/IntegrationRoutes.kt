package com.assistant.server.routes

import com.assistant.ai.AIOrchestrator
import com.assistant.ai.ConnectionStatus
import com.assistant.ai.OllamaAgent
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.jira.JiraProject
import com.assistant.jira.JiraRestClient
import com.assistant.kb.ProviderConfigRepository
import com.assistant.rbac.Permission
import com.assistant.server.middleware.withPermission
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class ProviderConfigUpdateRequest(
    val endpoint: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val priority: Int? = null
)

@Serializable
data class JiraConfigRequest(
    val domain: String,
    val email: String,
    val apiToken: String
)

@Serializable
data class JiraConfigResponse(
    val status: String,
    val projects: List<JiraProject> = emptyList(),
    val error: String? = null
)

@Serializable
data class ProviderTestRequest(
    val endpoint: String = "",
    val model: String = ""
)

@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModelInfo> = emptyList(),
    val error: String? = null
)

@Serializable
data class OllamaModelInfo(
    val name: String = "",
    val model: String = "",
    val size: Long = 0
)

@Serializable
data class ProviderTestResult(
    val providerId: String,
    val success: Boolean,
    val status: String,
    val latencyMs: Long = 0,
    val message: String = ""
)

@Serializable
data class JiraStatusResponse(
    val configured: Boolean,
    val domain: String = "",
    val status: String = "offline"
)

/**
 * Integration routes — manage AI providers and Jira connection.
 * GET  /api/integrations/jira/status       — check Jira config status (public, no JWT)
 * GET  /api/integrations                  — list provider statuses (Reader+)
 * POST /api/integrations/{providerId}/test — test provider connection (Administrator)
 * PUT  /api/integrations/{providerId}/config — update provider config (Administrator)
 * PUT  /api/integrations/jira/config       — save & validate Jira credentials (Administrator)
 */
fun Routing.integrationRoutes() {
    val orchestrator by inject<AIOrchestrator>()
    val providerConfigRepo by inject<ProviderConfigRepository>()
    val httpClient by inject<HttpClient>()

    route("/api/integrations") {
        // GET /jira/status — public endpoint (no JWT required)
        // Used by frontend on first-launch to check if Jira is configured
        get("/jira/status") {
            val jiraConfig = providerConfigRepo.findByType(com.assistant.ai.ProviderType.JIRA)
            if (jiraConfig != null) {
                call.respond(HttpStatusCode.OK, JiraStatusResponse(
                    configured = true,
                    domain = jiraConfig.endpoint,
                    status = jiraConfig.status.name.lowercase()
                ))
            } else {
                call.respond(HttpStatusCode.OK, JiraStatusResponse(
                    configured = false
                ))
            }
        }

        // GET — Reader+ can view provider statuses
        withPermission(Permission.VIEW_DASHBOARD) {
            get {
                // Always show 5 default providers, overridden by DB data where available
                val dbProviders = providerConfigRepo.getAllProviders()
                val dbMap = dbProviders.associateBy { it.providerId }

                val defaults = listOf(
                    ProviderConfig(providerId = "jira", name = "Jira Cloud Services", type = ProviderType.JIRA, endpoint = "", priority = 0, status = ConnectionStatus.STANDBY),
                    ProviderConfig(providerId = "ollama", name = "Ollama (Local)", type = ProviderType.OLLAMA, endpoint = "http://localhost:11434", model = "llama3", priority = 1, status = ConnectionStatus.OFFLINE),
                    ProviderConfig(providerId = "gemini", name = "Google Gemini API", type = ProviderType.GEMINI, endpoint = "", model = "gemini-1.5-pro", priority = 2, status = ConnectionStatus.OFFLINE),
                    ProviderConfig(providerId = "lm_studio", name = "LM Studio", type = ProviderType.LM_STUDIO, endpoint = "http://localhost:1234", priority = 3, status = ConnectionStatus.OFFLINE),
                    ProviderConfig(providerId = "gemini_cli", name = "Gemini CLI Interface", type = ProviderType.GEMINI_CLI, endpoint = "", priority = 4, status = ConnectionStatus.OFFLINE),
                    ProviderConfig(providerId = "embedding", name = "Embedding Model", type = ProviderType.EMBEDDING, endpoint = "http://localhost:11434", model = "nomic-embed-text", priority = 10, status = ConnectionStatus.ACTIVE)
                )

                // DB data overrides defaults; also include DB-only providers
                val defaultIds = defaults.map { it.providerId }.toSet()
                val merged = defaults.map { default -> dbMap[default.providerId] ?: default }
                val extraFromDb = dbProviders.filter { it.providerId !in defaultIds }
                val result = merged + extraFromDb

                call.respond(HttpStatusCode.OK, result)
            }
        }

        // POST test & PUT config — Administrator only
        withPermission(Permission.CONFIG_INTEGRATIONS) {
            // GET /api/integrations/ollama/models — fetch available models from Ollama
            get("/ollama/models") {
                val ollamaConfig = providerConfigRepo.findByType(com.assistant.ai.ProviderType.OLLAMA)
                val endpoint = ollamaConfig?.endpoint ?: "http://localhost:11434"
                try {
                    val tagsResp = httpClient.get("$endpoint/api/tags")
                    if (tagsResp.status.isSuccess()) {
                        call.respondText(tagsResp.bodyAsText(), ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.OK, OllamaModelsResponse(emptyList()))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.OK, OllamaModelsResponse(emptyList(), error = "Cannot connect to Ollama: ${e.message}"))
                }
            }

            post("/{providerId}/test") {
                val providerId = call.parameters["providerId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("providerId is required"))

                if (providerId == "jira") {
                    // Jira test: read credentials from DB and call Jira API
                    val jiraCredService = org.koin.java.KoinJavaComponent.getKoin().get<com.assistant.jira.JiraCredentialsService>()
                    val creds = jiraCredService.getJiraCredentials()
                    if (creds == null) {
                        call.respond(HttpStatusCode.OK, ProviderTestResult(
                            providerId = "jira",
                            success = false,
                            status = "OFFLINE",
                            message = "Jira not configured"
                        ))
                        return@post
                    }
                    try {
                        val token = java.util.Base64.getEncoder()
                            .encodeToString("${creds.email}:${creds.apiToken}".toByteArray())
                        val jiraClient = com.assistant.jira.JiraRestClient(httpClient, creds.domain, "Basic $token")
                        val projects = jiraClient.getProjects()
                        providerConfigRepo.updateStatus("jira", ConnectionStatus.ACTIVE)
                        call.respond(HttpStatusCode.OK, ProviderTestResult(
                            providerId = "jira",
                            success = true,
                            status = "ACTIVE",
                            message = "Connected — ${projects.size} projects accessible"
                        ))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.OK, ProviderTestResult(
                            providerId = "jira",
                            success = false,
                            status = "OFFLINE",
                            message = "Connection failed: ${e.message}"
                        ))
                    }
                } else {
                    // Read optional endpoint/model from request body for testing unsaved config
                    val testRequest = try {
                        call.receive<ProviderTestRequest>()
                    } catch (_: Exception) {
                        ProviderTestRequest()
                    }

                    if (testRequest.endpoint.isNotBlank()) {
                        // Test with provided endpoint (not saved config)
                        val testEndpoint = testRequest.endpoint
                        val testModel = testRequest.model.ifBlank { "llama3" }
                        try {
                            val agent = OllamaAgent(httpClient, testModel, testEndpoint)
                            val connResult = agent.testConnection()
                            if (connResult != null) {
                                call.respond(HttpStatusCode.OK, com.assistant.ai.ProviderTestResult(providerId, true, 0, connResult))
                            } else {
                                call.respond(HttpStatusCode.OK, com.assistant.ai.ProviderTestResult(providerId, false, 0, "Cannot connect to $testEndpoint"))
                            }
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.OK, com.assistant.ai.ProviderTestResult(providerId, false, 0, "Error: ${e.message}"))
                        }
                    } else {
                        val result = orchestrator.testProvider(providerId)
                        val status = if (result.success) ConnectionStatus.ACTIVE else ConnectionStatus.OFFLINE
                        providerConfigRepo.updateStatus(providerId, status)
                        call.respond(HttpStatusCode.OK, result)
                    }
                }
            }

            put("/{providerId}/config") {
                val providerId = call.parameters["providerId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("providerId is required"))

                val request = call.receive<ProviderConfigUpdateRequest>()

                // Determine provider type from existing config or defaults
                val existing = providerConfigRepo.findById(providerId)
                val providerType = existing?.type ?: when (providerId) {
                    "ollama" -> ProviderType.OLLAMA
                    "gemini" -> ProviderType.GEMINI
                    "lm_studio" -> ProviderType.LM_STUDIO
                    "gemini_cli" -> ProviderType.GEMINI_CLI
                    else -> ProviderType.OLLAMA
                }
                val providerName = existing?.name ?: providerId

                // Save config to DB
                val config = ProviderConfig(
                    providerId = providerId,
                    name = providerName,
                    type = providerType,
                    endpoint = request.endpoint ?: existing?.endpoint ?: "",
                    apiKey = request.apiKey ?: existing?.apiKey,
                    model = request.model ?: existing?.model,
                    priority = request.priority ?: existing?.priority ?: 0,
                    status = ConnectionStatus.STANDBY
                )
                providerConfigRepo.save(config)

                // Auto-test the connection
                val testResult = orchestrator.testProvider(providerId)
                val finalStatus = if (testResult.success) ConnectionStatus.ACTIVE else ConnectionStatus.OFFLINE
                providerConfigRepo.updateStatus(providerId, finalStatus)

                call.respond(HttpStatusCode.OK, ProviderTestResult(
                    providerId = providerId,
                    success = testResult.success,
                    status = finalStatus.name,
                    latencyMs = testResult.latencyMs,
                    message = if (testResult.success) "Configuration saved & connected" else "Configuration saved but connection failed: ${testResult.message}"
                ))
            }

            // PUT /api/integrations/jira/config — validate & save Jira credentials
            put("/jira/config") {
                val request = call.receive<JiraConfigRequest>()

                // Validate required fields
                if (request.domain.isBlank() || request.email.isBlank() || request.apiToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, JiraConfigResponse(
                        status = "offline",
                        error = "domain, email, and apiToken are required"
                    ))
                    return@put
                }

                // Normalize domain (ensure https:// prefix, strip trailing slash)
                val normalizedDomain = normalizeDomain(request.domain)

                // Build Basic Auth header: base64(email:apiToken)
                val credentials = "${request.email}:${request.apiToken}"
                val encoded = java.util.Base64.getEncoder().encodeToString(credentials.toByteArray())
                val authHeader = "Basic $encoded"

                // Validate credentials by calling Jira API /rest/api/3/project
                try {
                    val jiraClient = JiraRestClient(httpClient, normalizedDomain, authHeader)
                    val projects = jiraClient.getProjects()

                    if (projects.isEmpty()) {
                        // Could be valid credentials but no projects, or invalid credentials
                        // Try to distinguish by checking if we got an auth error
                        call.respond(HttpStatusCode.OK, JiraConfigResponse(
                            status = "offline",
                            error = "Authentication failed or no projects accessible. Verify your domain, email, and API token."
                        ))
                        return@put
                    }

                    // Credentials valid — save to provider_configs with encrypted apiToken
                    // Store domain in endpoint, email:apiToken in api_key (encrypted by ProviderConfigRepository)
                    val providerConfig = ProviderConfig(
                        providerId = "jira",
                        name = "Jira Cloud Services",
                        type = ProviderType.JIRA,
                        endpoint = normalizedDomain,
                        apiKey = "${request.email}:${request.apiToken}",
                        model = request.email, // Store email separately in model field for display
                        priority = 0,
                        status = ConnectionStatus.ACTIVE
                    )
                    providerConfigRepo.save(providerConfig)

                    call.respond(HttpStatusCode.OK, JiraConfigResponse(
                        status = "active",
                        projects = projects
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.OK, JiraConfigResponse(
                        status = "offline",
                        error = "Failed to connect to Jira: ${e.message ?: "Unknown error"}"
                    ))
                }
            }
        }
    }
}

/**
 * Normalize a Jira domain URL: ensure https:// prefix and strip trailing slash.
 */
private fun normalizeDomain(domain: String): String {
    val trimmed = domain.trim().trimEnd('/')
    return when {
        trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
        else -> "https://$trimmed"
    }
}
