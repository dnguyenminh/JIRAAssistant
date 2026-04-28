package com.assistant.server.di

import org.koin.dsl.module

/**
 * Koin module for the knowledge-graph sub-module.
 *
 * Currently empty — graph engine and repository bindings
 * are provided by :shared and :server:core.
 * This module exists as an extension point for future
 * KG-specific services (ontology management, graph CRUD).
 */
val knowledgeGraphModule = module {
    // All dependencies are provided by :shared and :server:core
}
