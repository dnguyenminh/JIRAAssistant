package com.assistant.server.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.ai.AIAgent
import com.assistant.ai.AIOrchestrator
import com.assistant.ai.AIOrchestratorImpl
import com.assistant.ai.OllamaAgent
import com.assistant.ai.aiModule
import com.assistant.auth.AuthService
import com.assistant.chat.*
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.McpServerRepositoryImpl
import com.assistant.server.mcp.McpProcessManagerImpl
import com.assistant.db.JiraDatabase
import com.assistant.domain.domainModule
import com.assistant.graph.ForceDirectedGraphEngine
import com.assistant.graph.GraphEngine
import com.assistant.jira.*
import com.assistant.kb.KBRepository
import com.assistant.kb.KBRepositoryImpl
import com.assistant.kb.ProviderConfigRepository
import com.assistant.scan.*
import com.assistant.settings.SettingsRepository
import com.assistant.settings.SettingsRepositoryImpl
import com.assistant.rbac.*
import com.assistant.server.attachment.*
import com.assistant.server.auth.AuthServiceImpl
import com.assistant.server.chat.ChatServiceImpl
import com.assistant.server.config.ServerConfig
import com.assistant.server.indexing.IndexingPipeline
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Base64

/**
 * Koin module for the Ktor server.
 * Composes shared modules and registers server-specific dependencies.
 */
fun serverModule(config: ServerConfig): Module = module {
    // Provide ServerConfig as a singleton
    single { config }

    // Include shared modules (aiModule provides HttpClient singleton)
    includes(aiModule, jiraModule, domainModule)

    // Auth Service
    single<AuthService> { AuthServiceImpl(get(), get()) }

    // RBAC
    single<AuditLogStore> { InMemoryAuditLogStore() }
    single<UserStore> { InMemoryUserStore() }
    single<RBACEngine> { RBACEngineImpl(get(), get()) }

    // SQLDelight Database
    single<JiraDatabase> {
        val dbPath = config.dbPath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
        try {
            JiraDatabase.Schema.create(driver)
        } catch (_: Exception) {
            // Schema already exists — run incremental migrations for new tables
            runIncrementalMigrations(driver)
        }
        JiraDatabase(driver)
    }

    // Knowledge Base Repository
    single<KBRepository> { KBRepositoryImpl(get()) }

    // Settings Repository
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }

    // Provider Config Repository (with encryption at rest)
    single { ProviderConfigRepository(get(), config.encryptionKey) }

    // Jira Credentials Service
    single { JiraCredentialsService(get()) }

    // JiraClient: factory that reads credentials from DB on each injection.
    // Uses factory (not singleton) so that after Jira config changes via API,
    // subsequent requests get a fresh JiraClient with updated credentials.
    factory<JiraClient> {
        val credentials = get<JiraCredentialsService>().getJiraCredentials()
        if (credentials != null) {
            val token = Base64.getEncoder()
                .encodeToString("${credentials.email}:${credentials.apiToken}".toByteArray())
            JiraRestClient(get<HttpClient>(), credentials.domain, "Basic $token")
        } else {
            NoOpJiraClient()
        }
    }

    // Graph Engine
    single<GraphEngine> { ForceDirectedGraphEngine() }

    // Scan Repositories
    single<ScanStateRepository> { ScanStateRepositoryImpl(get()) }
    single<ScanLogRepository> { ScanLogRepositoryImpl(get()) }

    // Attachment Processing Pipeline
    single<EmbeddingService> {
        val providerConfigRepo = get<ProviderConfigRepository>()
        EmbeddingServiceImpl(get<HttpClient>(), configProvider = {
            // Read EMBEDDING config from DB, fallback to OLLAMA endpoint
            val embConfig = providerConfigRepo.findByType(com.assistant.ai.ProviderType.EMBEDDING)
            if (embConfig != null) {
                EmbeddingServiceImpl.EmbeddingConfig(
                    model = embConfig.model ?: "nomic-embed-text",
                    endpoint = embConfig.endpoint
                )
            } else {
                val ollamaConfig = providerConfigRepo.findByType(com.assistant.ai.ProviderType.OLLAMA)
                EmbeddingServiceImpl.EmbeddingConfig(
                    model = "nomic-embed-text",
                    endpoint = ollamaConfig?.endpoint ?: "http://localhost:11434"
                )
            }
        })
    }
    single<VectorStore> { VectorStoreImpl(get()) }
    single<AttachmentDownloader> { AttachmentDownloaderImpl(get<HttpClient>()) }
    single {
        val credentialsService = get<JiraCredentialsService>()
        AttachmentPipeline(
            downloader = get(),
            embeddingService = get(),
            vectorStore = get(),
            mcpProcessManager = get(),
            scanLogRepository = get(),
            jiraAuthProvider = {
                val creds = credentialsService.getJiraCredentials()
                if (creds != null) {
                    val token = Base64.getEncoder()
                        .encodeToString("${creds.email}:${creds.apiToken}".toByteArray())
                    "Basic $token"
                } else null
            },
            markitdownIdResolver = {
                // Find markitdown MCP server by name (case-insensitive) since ID may vary
                val mcpRepo = get<com.assistant.mcp.McpServerRepository>()
                kotlinx.coroutines.runBlocking {
                    mcpRepo.getAll()
                        .firstOrNull { it.name.equals("markitdown", ignoreCase = true) }
                        ?.id
                }
            }
        )
    }

    // Indexing Pipeline — indexes tickets, relationships, analysis into VectorStore. Req: 12.1, 16.1
    single {
        IndexingPipeline(
            embeddingService = get(),
            vectorStore = get(),
            graphEngine = get()
        )
    }

    // Batch Scan Engine — uses SupervisorJob so individual scan failures don't cancel the whole scope
    // jiraClientProvider creates a fresh JiraClient each time by reading credentials from DB
    single {
        val credentialsService = get<JiraCredentialsService>()
        val httpClient = get<HttpClient>()
        val pipeline = get<AttachmentPipeline>()
        val indexingPipeline = get<IndexingPipeline>()
        val kbRepository = get<KBRepository>()
        BatchScanEngine(
            aiOrchestrator = get(),
            kbRepository = kbRepository,
            jiraClientProvider = {
                val credentials = credentialsService.getJiraCredentials()
                if (credentials != null) {
                    val token = Base64.getEncoder()
                        .encodeToString("${credentials.email}:${credentials.apiToken}".toByteArray())
                    JiraRestClient(httpClient, credentials.domain, "Basic $token")
                } else {
                    NoOpJiraClient()
                }
            },
            featureNetworkMapper = get(),
            scanStateRepository = get(),
            scanLogRepository = get(),
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            attachmentProcessor = { projectKey, ticketKey, attachments ->
                pipeline.processAttachments(projectKey, ticketKey, attachments)
            },
            onScanComplete = { projectKey ->
                val graph = kbRepository.getGraphData(projectKey) ?: return@BatchScanEngine
                indexingPipeline.reindex(projectKey, graph, emptyList())
            },
            onKBRecordSaved = { projectKey, record ->
                indexingPipeline.indexAnalysisResults(projectKey, listOf(record))
            }
        )
    }

    // Chat Repository — per-user persistent history in SQLDelight
    single<ChatRepository> { ChatRepositoryImpl(get()) }

    // Chat Conversation Repository — multi-conversation support
    single<ChatConversationRepository> { ChatConversationRepositoryImpl(get()) }

    // User AI Config Repository — per-user personalization
    single<UserAIConfigRepository> { UserAIConfigRepositoryImpl(get()) }

    // MCP Server Repository
    single<McpServerRepository> { McpServerRepositoryImpl(get()) }

    // MCP Process Manager — lifecycle management for MCP server processes
    single<McpProcessManager> {
        McpProcessManagerImpl(
            mcpRepo = get(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        )
    }

    // Chat Service — AI chat with KB + Graph context + personalization + MCP tools + attachments
    single<ChatService> {
        val httpClient = get<HttpClient>()
        val providerConfigRepo = get<ProviderConfigRepository>()
        ChatServiceImpl(
            aiAgentProvider = {
                val config = providerConfigRepo.findByType(com.assistant.ai.ProviderType.OLLAMA)
                val model = config?.model ?: "llama3"
                val endpoint = config?.endpoint ?: "http://localhost:11434"
                OllamaAgent(httpClient, model, endpoint)
            },
            kbRepository = get(),
            graphEngine = get(),
            userAIConfigRepository = get(),
            mcpProcessManager = get(),
            embeddingService = get(),
            vectorStore = get(),
            indexingPipeline = get()
        )
    }

    // AI Orchestrator — creates agents dynamically from DB config on each call
    single<AIOrchestrator> {
        val kbRepo = get<KBRepository>()
        val httpClient = get<HttpClient>()
        val providerConfigRepo = get<ProviderConfigRepository>()

        AIOrchestratorImpl(
            kbRepository = kbRepo,
            agents = emptyMap(),
            agentProvider = {
                // Read fresh config from DB each time
                val agents = mutableMapOf<String, AIAgent>()
                val allConfigs = providerConfigRepo.getAllProviders()
                for (config in allConfigs) {
                    when (config.type) {
                        com.assistant.ai.ProviderType.OLLAMA -> {
                            agents[config.providerId] = OllamaAgent(
                                httpClient,
                                config.model ?: "llama3",
                                config.endpoint
                            )
                        }
                        else -> {
                            agents[config.providerId] = OllamaAgent(
                                httpClient,
                                config.model ?: "llama3",
                                config.endpoint
                            )
                        }
                    }
                }
                if (agents.isEmpty()) {
                    agents["ollama"] = OllamaAgent(httpClient, "llama3", "http://localhost:11434")
                }
                agents
            },
            providerConfigProvider = {
                // Read fresh provider configs from DB each time
                val configs = providerConfigRepo.getAllProviders()
                configs.ifEmpty {
                    // Fallback: create default Ollama config as ACTIVE
                    listOf(
                        com.assistant.ai.ProviderConfig(
                            providerId = "ollama",
                            name = "Ollama",
                            type = com.assistant.ai.ProviderType.OLLAMA,
                            endpoint = "http://localhost:11434",
                            model = "llama3",
                            priority = 0,
                            status = com.assistant.ai.ConnectionStatus.ACTIVE
                        )
                    )
                }
            }
        )
    }
}
