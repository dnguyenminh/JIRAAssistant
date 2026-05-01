package com.assistant.server.di

import com.assistant.chat.ChatService
import com.assistant.server.chat.ChatServiceImpl
import com.assistant.server.chat.LocalKBToolExecutor
import com.assistant.server.chat.UserToolPermissionService
import org.koin.dsl.module

/**
 * Koin module for the server/chat sub-module.
 * Provides chat service, tool permission service, and local KB tool executor.
 * Requirements: 19.5, 3.2, 19.64
 */
val chatKoinModule = module {
    single { UserToolPermissionService(permRepo = get(), mcpServerRepo = get()) }
    single { LocalKBToolExecutor(embeddingService = get(), vectorStore = get(), kbRepository = get()) }
    single<ChatService> {
        val http = get<io.ktor.client.HttpClient>()
        val pcr = get<com.assistant.kb.ProviderConfigRepository>()
        ChatServiceImpl(
            aiAgentProvider = {
                val agents = buildChatAgentMap(pcr, http)
                val activeByPriority = pcr.getAllProviders()
                    .filter { it.status == com.assistant.ai.ConnectionStatus.ACTIVE }
                    .filter {
                        it.type in listOf(
                            com.assistant.ai.ProviderType.OLLAMA,
                            com.assistant.ai.ProviderType.LM_STUDIO,
                            com.assistant.ai.ProviderType.GEMINI,
                            com.assistant.ai.ProviderType.GEMINI_CLI,
                            com.assistant.ai.ProviderType.COPILOT_CLI,
                            com.assistant.ai.ProviderType.KIRO_CLI
                        )
                    }
                    .sortedBy { it.priority }
                val bestConfig = activeByPriority.firstOrNull()
                if (bestConfig != null) {
                    agents[bestConfig.providerId]
                        ?: com.assistant.ai.OllamaAgent(http, "llama3", "http://localhost:11434")
                } else {
                    com.assistant.ai.OllamaAgent(http, "llama3", "http://localhost:11434")
                }
            },
            kbRepository = get(),
            graphEngine = get(),
            userAIConfigRepository = get(),
            mcpProcessManager = get(),
            embeddingService = get(),
            vectorStore = get(),
            indexingPipeline = get(),
            settingsRepository = get(),
            localKBToolExecutor = get(),
            providerConfigRepository = pcr,
            internalMcpBridge = get(),
            mcpServerRepository = get(),
            userToolPermissionService = get()
        )
    }
}

private fun buildChatAgentMap(
    pcr: com.assistant.kb.ProviderConfigRepository,
    http: io.ktor.client.HttpClient
): Map<String, com.assistant.ai.AIAgent> {
    val agents = mutableMapOf<String, com.assistant.ai.AIAgent>()
    for (config in pcr.getAllProviders()) {
        when (config.type) {
            com.assistant.ai.ProviderType.OLLAMA,
            com.assistant.ai.ProviderType.LM_STUDIO ->
                agents[config.providerId] = com.assistant.ai.OllamaAgent(
                    http, config.model ?: "llama3", config.endpoint
                )
            com.assistant.ai.ProviderType.GEMINI ->
                agents[config.providerId] = com.assistant.ai.OllamaAgent(
                    http, config.model ?: "gemma2", config.endpoint
                )
            com.assistant.ai.ProviderType.GEMINI_CLI ->
                agents[config.providerId] = com.assistant.server.ai.GeminiCliAgent(
                    config.endpoint, config.model ?: "gemini-2.0-flash"
                )
            com.assistant.ai.ProviderType.COPILOT_CLI ->
                agents[config.providerId] = com.assistant.server.ai.CopilotCliAgent(
                    config.endpoint, config.model ?: "copilot"
                )
            com.assistant.ai.ProviderType.KIRO_CLI ->
                agents[config.providerId] = com.assistant.server.ai.KiroCliAgent(
                    config.endpoint, config.model ?: "kiro"
                )
            else -> {}
        }
    }
    if (agents.isEmpty()) {
        agents["ollama"] = com.assistant.ai.OllamaAgent(http, "llama3", "http://localhost:11434")
    }
    return agents
}
