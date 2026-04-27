package com.assistant.server.agent.di

import com.assistant.agent.engine.ThinkingLoopEngine
import com.assistant.agent.home.AgentHomeDirectory
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.registry.AgentRegistry
import com.assistant.agent.session.SessionManager
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.tool.ToolRegistry
import com.assistant.server.agent.engine.ParallelToolExecutor
import com.assistant.server.agent.engine.ThinkingLoopEngineImpl
import com.assistant.server.agent.error.ErrorHandler
import com.assistant.server.agent.home.AgentHomeDirectoryLoader
import com.assistant.server.agent.home.AgentHomeDirectoryWatcher
import com.assistant.server.agent.home.AgentMcpManager
import com.assistant.server.agent.progress.NoOpProgressReporter
import com.assistant.server.agent.registry.AgentRegistryImpl
import com.assistant.server.agent.session.SessionManagerImpl
import com.assistant.server.agent.state.AgentStateManager
import com.assistant.server.agent.streaming.StreamingOutputAdapter
import com.assistant.server.agent.subprocess.MessageProtocol
import com.assistant.server.agent.subprocess.SubprocessManagerImpl
import com.assistant.server.agent.subprocess.SubprocessProxyImpl
import com.assistant.server.agent.tool.ToolRegistryImpl
import org.koin.dsl.module

/**
 * Koin module for the Generic Agent Framework.
 *
 * **Phase 1** — core agent components (registry, tools, engine, orchestrators).
 * **Phase 2** — subprocess management, agent home directory, sessions, streaming.
 *
 * - **single**: singletons shared across the application
 * - **factory**: per-request instances (new per injection)
 *
 * Follows existing [com.assistant.server.di.serverModule] patterns.
 */
val agentModule = module {

    // ── Phase 1 — Singletons ────────────────────────────────────
    single<AgentRegistry> { AgentRegistryImpl() }

    // ── Phase 1 — Per-request factories ─────────────────────────
    factory<ToolRegistry> { ToolRegistryImpl() }
    factory { ParallelToolExecutor(get()) }
    factory<ThinkingLoopEngine> { ThinkingLoopEngineImpl() }
    factory<ProgressReporter> { NoOpProgressReporter() }
    factory { ErrorHandler() }
    factory { AgentStateManager() }

    // ── Phase 2 — Singletons ────────────────────────────────────
    single<SubprocessManager> { SubprocessManagerImpl(configs = emptyMap()) }
    single<SessionManager> { SessionManagerImpl() }
    single { MessageProtocol }

    // ── Phase 2 — Per-request factories ─────────────────────────
    factory<SubprocessProxy> { SubprocessProxyImpl(get(), get()) }
    factory<AgentHomeDirectory> { params ->
        AgentHomeDirectoryLoader(basePath = params.get())
    }
    factory { params ->
        AgentHomeDirectoryWatcher(loader = params.get(), basePath = params.get())
    }
    factory { AgentMcpManager(get(), get()) }
    factory { StreamingOutputAdapter(get()) }
}
