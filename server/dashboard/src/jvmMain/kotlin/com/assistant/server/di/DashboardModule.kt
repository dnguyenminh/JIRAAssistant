package com.assistant.server.di

import org.koin.dsl.module

/**
 * Koin module for the dashboard sub-module.
 *
 * Dashboard routes (scan, estimation, graph) depend on services
 * already provided by :shared and :server:core (BatchScanEngine,
 * ScrumEstimator, GraphEngine, KBRepository, AIOrchestrator).
 *
 * No additional dashboard-specific bindings are needed.
 */
val dashboardModule = module {
    // All dependencies are provided by :shared and :server:core
}
