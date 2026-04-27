package com.assistant.server.routes

import com.assistant.chat.UserAIConfig
import com.assistant.chat.UserAIConfigRepository
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.settings.SettingsRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Chat config, model-info, and tools endpoints.
 * Requirements: 19.40, 19.54, 19.60
 */
internal fun Route.chatConfigRoutes(userAIConfigRepo: UserAIConfigRepository) {
    get("/config") {
        val (userId, _) = extractUserClaims() ?: return@get
        val config = userAIConfigRepo.findByUserId(userId) ?: UserAIConfig(userId = userId)
        call.respond(HttpStatusCode.OK, config)
    }
    put("/config") {
        val (userId, _) = extractUserClaims() ?: return@put
        val body = call.receive<UserAIConfig>()
        userAIConfigRepo.save(body.copy(userId = userId))
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }
}

internal suspend fun RoutingContext.handleModelInfo(providerConfigRepo: ProviderConfigRepository) {
    extractUserClaims() ?: return
    val activeProvider = findActiveProvider(providerConfigRepo)
    call.respond(HttpStatusCode.OK, activeProvider)
}

private fun findActiveProvider(repo: ProviderConfigRepository): ModelInfo {
    val providers = repo.getAllProviders()
    val active = providers.firstOrNull { it.status == com.assistant.ai.ConnectionStatus.ACTIVE }
    if (active != null) {
        return ModelInfo(
            modelName = active.model ?: "unknown",
            provider = active.providerId,
            supportsVision = detectVision(active.model),
            supportsTools = false,
            maxTokens = 8192
        )
    }
    return ModelInfo("none", "none", false, false, 0)
}

private fun detectVision(model: String?): Boolean {
    if (model == null) return false
    val visionKeywords = listOf("vision", "4o", "gpt-4", "gemini-1.5-pro", "llava")
    return visionKeywords.any { model.contains(it, ignoreCase = true) }
}

internal suspend fun RoutingContext.handleGetTools() {
    extractUserClaims() ?: return
    val processManager by call.application.inject<McpProcessManager>()
    val settingsRepo by call.application.inject<SettingsRepository>()
    val internalBridge by call.application.inject<com.assistant.server.mcp.internal.InternalMcpBridge>()
    val internalTools = internalBridge.getAggregatedTools()
        .map { ToolInfo(it.name, it.serverName, it.description) }
    val externalTools = processManager.getActiveTools()
        .map { ToolInfo(it.name, it.serverName, it.description) }
    val tools = (internalTools + externalTools).toMutableList()
    tools.addAll(buildLocalKBToolInfos(settingsRepo))
    call.respond(HttpStatusCode.OK, tools)
}

/** Add local KB tool entries when enabled. Req: 19.62, 19.63 */
internal suspend fun buildLocalKBToolInfos(settingsRepo: SettingsRepository): List<ToolInfo> {
    val value = settingsRepo.get("local_kb_tool_enabled")
    if (value == "false") return emptyList()
    return listOf(
        ToolInfo("search_knowledge", "local-knowledge-base", "Tìm kiếm semantic trong Knowledge Base cục bộ"),
        ToolInfo("get_ticket_info", "local-knowledge-base", "Tra cứu thông tin phân tích ticket từ KB"),
        ToolInfo("search_relationships", "local-knowledge-base", "Tìm kiếm mối quan hệ/dependency giữa tickets"),
        ToolInfo("ingest_knowledge", "local-knowledge-base", "Ghi nội dung vào KB để chia sẻ dữ liệu giữa các phases")
    )
}

@Serializable
data class ModelInfo(
    val modelName: String,
    val provider: String,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val maxTokens: Int
)

@Serializable
data class ToolInfo(
    val toolName: String,
    val serverName: String,
    val description: String
)

/**
 * Extract userId and role from JWT claims. Returns null and responds 401 if missing.
 */
internal suspend fun RoutingContext.extractUserClaims(): Pair<String, String>? {
    val principal = call.principal<JWTPrincipal>()
    val userId = principal?.payload?.getClaim("user_id")?.asString()
    val role = principal?.payload?.getClaim("role")?.asString()
    if (userId == null || role == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
        return null
    }
    return userId to role
}
