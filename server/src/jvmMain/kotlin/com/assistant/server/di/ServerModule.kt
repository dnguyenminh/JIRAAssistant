package com.assistant.server.di

import com.assistant.ai.aiModule
import com.assistant.domain.domainModule
import com.assistant.server.agent.ba.baAgentModule
import com.assistant.server.agent.di.agentModule
import com.assistant.server.config.ServerConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Root Koin module — composes all sub-module Koin modules.
 *
 * Domain-specific bindings live in their respective sub-modules.
 * Cross-cutting bindings (Jira, AI, Graph, BatchScan) live in
 * [aggregatorBindingsModule].
 */
fun serverModule(config: ServerConfig): Module = module {
    single { config }

    includes(
        // ── Shared modules ──
        aiModule,
        domainModule,

        // ── Core platform ──
        coreModule(config),

        // ── Feature sub-modules ──
        dashboardModule,
        analysisModule,
        docgenModule,
        agentModule,
        baAgentModule,
        chatKoinModule,
        mcpKoinModule,
        knowledgeGraphModule,
        userMgmtModule,

        // ── Aggregator cross-cutting bindings ──
        aggregatorBindingsModule,
    )
}
