package com.assistant.server.di

import com.assistant.ai.*
import com.assistant.auth.AuthService
import com.assistant.server.ai.CopilotCliAgent
import com.assistant.server.ai.GeminiCliAgent
import com.assistant.server.ai.KiroCliAgent
import com.assistant.chat.ChatService
import com.assistant.domain.domainModule
import com.assistant.graph.ForceDirectedGraphEngine
import com.assistant.graph.GraphEngine
import com.assistant.jira.*
import com.assistant.kb.KBRepository
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.rbac.*
import com.assistant.scan.BatchScanEngine
import com.assistant.scan.IncrementalGraphBuilder
import com.assistant.server.attachment.*
import com.assistant.server.auth.AuthServiceImpl
import com.assistant.server.chat.ChatServiceImpl
import com.assistant.server.chat.LocalKBToolExecutor
import com.assistant.server.chat.UserToolPermissionService
import com.assistant.server.config.ServerConfig
import com.assistant.server.indexing.IndexingPipeline
import com.assistant.server.mcp.McpProcessManagerImpl
import com.assistant.server.agent.ba.baAgentModule
import com.assistant.server.agent.di.agentModule
import com.assistant.server.document.curation.curationModule
import com.assistant.server.jobs.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Base64

/**
 * Koin module for the Ktor server. Wires PostgreSQL persistence
 * and all application services.
 */
fun serverModule(config: ServerConfig): Module = module {
    single { config }
    includes(aiModule, domainModule, agentModule, baAgentModule, curationModule)

    // PostgreSQL persistence
    includes(postgresModule(config))

    // Auth & RBAC
    single<AuthService> { AuthServiceImpl(get(), get()) }
    single<AuditLogStore> { FileBasedAuditLogStore("data") }
    single<UserStore> { InMemoryUserStore() }
    single<RBACEngine> { RBACEngineImpl(get(), get()) }

    // Jira
    single { JiraCredentialsService(get()) }
    // Jira — reads credentials from DB each request.
    // jiraModule provides a NoOpJiraClient fallback factory;
    // this factory overrides it when credentials are configured.
    factory<JiraClient> {
        val credService = get<JiraCredentialsService>()
        val creds = credService.getJiraCredentials()
        if (creds != null) {
            val token = Base64.getEncoder()
                .encodeToString("${creds.email}:${creds.apiToken}".toByteArray())
            JiraRestClient(get<HttpClient>(), creds.domain, "Basic $token")
        } else NoOpJiraClient()
    }
    single<GraphEngine> { ForceDirectedGraphEngine() }

    // Embedding & Attachment Pipeline
    single<EmbeddingService> {
        val repo = get<ProviderConfigRepository>()
        EmbeddingServiceImpl(get<HttpClient>(), configProvider = {
            val emb = repo.findByType(ProviderType.EMBEDDING)
            if (emb != null) EmbeddingServiceImpl.EmbeddingConfig(emb.model ?: "nomic-embed-text", emb.endpoint)
            else {
                val ollama = repo.findByType(ProviderType.OLLAMA)
                EmbeddingServiceImpl.EmbeddingConfig("nomic-embed-text", ollama?.endpoint ?: "http://localhost:11434")
            }
        })
    }
    single<AttachmentDownloader> { AttachmentDownloaderImpl(get<HttpClient>()) }
    single {
        val credSvc = get<JiraCredentialsService>()
        AttachmentPipeline(
            downloader = get(), embeddingService = get(), vectorStore = get(),
            mcpProcessManager = get(), scanLogRepository = get(),
            jiraAuthProvider = {
                val c = credSvc.getJiraCredentials() ?: return@AttachmentPipeline null
                val t = Base64.getEncoder().encodeToString("${c.email}:${c.apiToken}".toByteArray())
                "Basic $t"
            },
            markitdownIdResolver = {
                val mcpRepo = get<com.assistant.mcp.McpServerRepository>()
                kotlinx.coroutines.runBlocking {
                    mcpRepo.getAll().firstOrNull { it.name.equals("markitdown", ignoreCase = true) }?.id
                }
            }
        )
    }
    single { IndexingPipeline(embeddingService = get(), vectorStore = get(), graphEngine = get()) }

    // Document Aggregator — delegated to DeepCollectionModule (Req 8.1, 12.1)
    // FeatureFlagAggregator switches between DeepCollector and DocumentAggregatorImpl
    includes(deepCollectionModule)

    // Job Manager components (Document Job Manager feature)
    single { DependencyChecker }
    single { JobChainOrchestrator(jobRepository = get()) }
    single {
        JobExecutor(
            aggregator = get(), documentRepository = get(), jobRepository = get(),
            settingsRepository = get(), subprocessOrchestrator = getOrNull(),
            kbRepository = get(), aiOrchestrator = getOrNull()
        )
    }
    single {
        JobManager(
            jobRepository = get(), documentRepository = get(),
            jobExecutor = get(), chainOrchestrator = get(),
            dependencyChecker = get()
        )
    }

    // Batch Scan Engine
    single {
        val credSvc = get<JiraCredentialsService>()
        val http = get<HttpClient>()
        val pipeline = get<AttachmentPipeline>()
        val idxPipeline = get<IndexingPipeline>()
        val kbRepo = get<KBRepository>()
        val graphHolder = get<com.assistant.server.document.TicketGraphHolder>()
        val linkedProcessor = get<com.assistant.server.attachment.LinkedAttachmentProcessor>()
        BatchScanEngine(
            aiOrchestrator = get(), kbRepository = kbRepo,
            jiraClientProvider = {
                val c = credSvc.getJiraCredentials()
                if (c != null) {
                    val t = Base64.getEncoder().encodeToString("${c.email}:${c.apiToken}".toByteArray())
                    JiraRestClient(http, c.domain, "Basic $t")
                } else NoOpJiraClient()
            },
            featureNetworkMapper = get(), scanStateRepository = get(), scanLogRepository = get(),
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            attachmentProcessor = { projectKey, ticketKey, attachments ->
                pipeline.processAttachments(projectKey, ticketKey, attachments)
            },
            onScanComplete = { projectKey ->
                val g = kbRepo.getGraphData(projectKey) ?: return@BatchScanEngine
                idxPipeline.reindex(projectKey, g, emptyList())
            },
            onKBRecordSaved = { projectKey, record ->
                idxPipeline.indexAnalysisResults(projectKey, listOf(record))
            },
            settingsRepository = get(), jiraContentExtractor = get(),
            linkedAttachmentProcessor = { ticketId ->
                val graph = graphHolder.take(ticketId) ?: return@BatchScanEngine
                if (graph.nodes.size > 1) {
                    linkedProcessor.processLinkedAttachments(graph, ticketId, asBackground = false)
                }
            }
        ).also { engine ->
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            engine.incrementalGraphBuilder = IncrementalGraphBuilder(engine, scope)
        }
    }

    // Chat & MCP
    single { UserToolPermissionService(permRepo = get(), mcpServerRepo = get()) }
    single<McpProcessManager> { McpProcessManagerImpl(get(), CoroutineScope(Dispatchers.IO + SupervisorJob())) }
    single { com.assistant.server.mcp.internal.InternalToolRegistry() }
    single {
        com.assistant.server.mcp.internal.InternalMcpToolExecutor(
            toolRegistry = get(), rbacEngine = get(), batchScanEngine = get(), aiOrchestrator = get(),
            chatServiceProvider = { get() }, chatRepository = get(), conversationRepository = get(),
            settingsRepository = get(), providerConfigRepo = get(), mcpProcessManager = get(),
            mcpServerRepo = get(), kbRepository = get(), userStore = get()
        )
    }
    single { com.assistant.server.mcp.internal.InternalMcpBridge(executor = get(), mcpRepo = get()) }
    single { com.assistant.server.mcp.McpHealthChecker(get(), get(), get()) }
    single { LocalKBToolExecutor(embeddingService = get(), vectorStore = get(), kbRepository = get()) }
    single<ChatService> {
        val http = get<HttpClient>()
        val pcr = get<ProviderConfigRepository>()
        ChatServiceImpl(
            aiAgentProvider = {
                val agents = buildAgentMap(pcr, http)
                val activeByPriority = pcr.getAllProviders()
                    .filter { it.status == ConnectionStatus.ACTIVE }
                    .filter { it.type in listOf(ProviderType.OLLAMA, ProviderType.LM_STUDIO, ProviderType.GEMINI, ProviderType.GEMINI_CLI, ProviderType.COPILOT_CLI, ProviderType.KIRO_CLI) }
                    .sortedBy { it.priority }
                val bestConfig = activeByPriority.firstOrNull()
                if (bestConfig != null) {
                    agents[bestConfig.providerId]
                        ?: OllamaAgent(http, "llama3", "http://localhost:11434")
                } else {
                    OllamaAgent(http, "llama3", "http://localhost:11434")
                }
            },
            kbRepository = get(), graphEngine = get(), userAIConfigRepository = get(),
            mcpProcessManager = get(), embeddingService = get(), vectorStore = get(),
            indexingPipeline = get(), settingsRepository = get(), localKBToolExecutor = get(),
            providerConfigRepository = pcr, internalMcpBridge = get(),
            mcpServerRepository = get(), userToolPermissionService = get()
        )
    }

    // Deep Analysis
    single { com.assistant.ai.deepanalysis.SectionClassifierImpl() as com.assistant.ai.deepanalysis.SectionClassifier }
    // Legacy JiraContentExtractorImpl — used by FeatureFlagContentExtractor when deep collection disabled
    single {
        val credSvc = get<JiraCredentialsService>()
        val http = get<HttpClient>()
        com.assistant.ai.deepanalysis.JiraContentExtractorImpl(jiraClientProvider = {
            val c = credSvc.getJiraCredentials()
            if (c != null) {
                val t = Base64.getEncoder().encodeToString("${c.email}:${c.apiToken}".toByteArray())
                JiraRestClient(http, c.domain, "Basic $t")
            } else NoOpJiraClient()
        }, sectionClassifier = get())
    }
    // Feature-flag-aware JiraContentExtractor — delegates to deep or legacy
    single<com.assistant.ai.deepanalysis.JiraContentExtractor> {
        get<com.assistant.server.document.FeatureFlagContentExtractor>()
    }
    single<com.assistant.ai.deepanalysis.DeepAnalysisPromptBuilder> { com.assistant.ai.deepanalysis.DeepAnalysisPromptBuilderImpl() }
    single<com.assistant.ai.deepanalysis.DeepAnalysisResponseParser> { com.assistant.ai.deepanalysis.DeepAnalysisResponseParserImpl() }
    single { kotlinx.coroutines.sync.Semaphore(1) }
    single<com.assistant.ai.deepanalysis.CascadingAnalysisEngine> { com.assistant.ai.deepanalysis.CascadingAnalysisEngineImpl(get(), get(), get(), get()) }

    // AI Orchestrator
    single<AIOrchestrator> {
        val kbRepo = get<KBRepository>()
        val http = get<HttpClient>()
        val pcr = get<ProviderConfigRepository>()
        AIOrchestratorImpl(
            kbRepository = kbRepo, agents = emptyMap(),
            jiraContentExtractor = get(), deepPromptBuilder = get(), deepResponseParser = get(),
            agentProvider = { buildAgentMap(pcr, http) },
            providerConfigProvider = { buildProviderConfigs(pcr) },
            mapReduceAnalyzer = get()
        )
    }
}

private fun buildAgentMap(pcr: ProviderConfigRepository, http: HttpClient): Map<String, AIAgent> {
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
    if (agents.isEmpty()) agents["ollama"] = OllamaAgent(http, "llama3", "http://localhost:11434")
    return agents
}

private fun buildProviderConfigs(pcr: ProviderConfigRepository): List<ProviderConfig> {
    val configs = pcr.getAllProviders()
    return configs.ifEmpty {
        listOf(
            ProviderConfig("ollama", "Ollama", ProviderType.OLLAMA, "http://localhost:11434",
                model = "llama3", priority = 0, status = ConnectionStatus.ACTIVE)
        )
    }
}
