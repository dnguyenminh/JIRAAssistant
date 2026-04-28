package com.assistant.server.di

import com.assistant.ai.*
import com.assistant.ai.deepanalysis.*
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.jira.*
import com.assistant.kb.KBRepository
import com.assistant.kb.ProviderConfigRepository
import com.assistant.scan.BatchScanEngine
import com.assistant.scan.IncrementalGraphBuilder
import com.assistant.server.ai.CopilotCliAgent
import com.assistant.server.ai.GeminiCliAgent
import com.assistant.server.ai.KiroCliAgent
import com.assistant.server.attachment.AttachmentPipeline
import com.assistant.server.attachment.LinkedAttachmentProcessor
import com.assistant.server.indexing.IndexingPipeline
import com.assistant.settings.SettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Base64

/** Build the agent map from all configured providers. */
internal fun buildAgentMap(
    pcr: ProviderConfigRepository,
    http: HttpClient,
): Map<String, AIAgent> {
    val agents = mutableMapOf<String, AIAgent>()
    for (config in pcr.getAllProviders()) {
        when (config.type) {
            ProviderType.OLLAMA, ProviderType.LM_STUDIO ->
                agents[config.providerId] = OllamaAgent(http, config.model ?: "llama3", config.endpoint)
            ProviderType.GEMINI ->
                agents[config.providerId] = OllamaAgent(http, config.model ?: "gemma2", config.endpoint)
            ProviderType.GEMINI_CLI ->
                agents[config.providerId] = GeminiCliAgent(config.endpoint, config.model ?: "gemini-2.0-flash")
            ProviderType.COPILOT_CLI ->
                agents[config.providerId] = CopilotCliAgent(config.endpoint, config.model ?: "copilot")
            ProviderType.KIRO_CLI ->
                agents[config.providerId] = KiroCliAgent(config.endpoint, config.model ?: "kiro")
            else -> {}
        }
    }
    if (agents.isEmpty()) {
        agents["ollama"] = OllamaAgent(http, "llama3", "http://localhost:11434")
    }
    return agents
}

/** Build provider config list with fallback default. */
internal fun buildProviderConfigs(
    pcr: ProviderConfigRepository,
): List<ProviderConfig> {
    val configs = pcr.getAllProviders()
    return configs.ifEmpty {
        listOf(
            ProviderConfig(
                "ollama", "Ollama", ProviderType.OLLAMA, "http://localhost:11434",
                model = "llama3", priority = 0, status = ConnectionStatus.ACTIVE
            )
        )
    }
}

/** Deep analysis bindings (SectionClassifier, extractors, etc.) */
internal fun deepAnalysisBindings(): Module = module {
    single<SectionClassifier> { SectionClassifierImpl() }
    single {
        val credSvc = get<JiraCredentialsService>()
        val http = get<HttpClient>()
        JiraContentExtractorImpl(
            jiraClientProvider = {
                val c = credSvc.getJiraCredentials()
                if (c != null) {
                    val t = Base64.getEncoder()
                        .encodeToString("${c.email}:${c.apiToken}".toByteArray())
                    JiraRestClient(http, c.domain, "Basic $t")
                } else NoOpJiraClient()
            },
            sectionClassifier = get()
        )
    }
    single<JiraContentExtractor> {
        get<com.assistant.server.document.FeatureFlagContentExtractor>()
    }
    single<DeepAnalysisPromptBuilder> { DeepAnalysisPromptBuilderImpl() }
    single<DeepAnalysisResponseParser> { DeepAnalysisResponseParserImpl() }
    single { kotlinx.coroutines.sync.Semaphore(1) }
    single<CascadingAnalysisEngine> {
        CascadingAnalysisEngineImpl(get(), get(), get(), get())
    }
}
