package com.assistant.server.di

import com.assistant.mcp.McpProcessManager
import com.assistant.server.mcp.McpHealthChecker
import com.assistant.server.mcp.McpProcessManagerImpl
import com.assistant.server.mcp.internal.InternalMcpBridge
import com.assistant.server.mcp.internal.InternalMcpToolExecutor
import com.assistant.server.mcp.internal.InternalToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Koin module for MCP sub-module: process manager, health checker,
 * internal tool registry, and internal MCP bridge bindings.
 */
val mcpKoinModule = module {
    single<McpProcessManager> {
        McpProcessManagerImpl(get(), CoroutineScope(Dispatchers.IO + SupervisorJob()))
    }
    single { InternalToolRegistry() }
    single {
        InternalMcpToolExecutor(
            toolRegistry = get(),
            rbacEngine = get(),
            batchScanEngine = get(),
            aiOrchestrator = get(),
            chatServiceProvider = { get() },
            chatRepository = get(),
            conversationRepository = get(),
            settingsRepository = get(),
            providerConfigRepo = get(),
            mcpProcessManager = get(),
            mcpServerRepo = get(),
            kbRepository = get(),
            userStore = get()
        )
    }
    single { InternalMcpBridge(executor = get(), mcpRepo = get()) }
    single { McpHealthChecker(get(), get(), get()) }
}
