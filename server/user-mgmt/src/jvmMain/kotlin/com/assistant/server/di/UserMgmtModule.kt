package com.assistant.server.di

import org.koin.dsl.module

/**
 * Koin module for the user-mgmt sub-module.
 *
 * User management routes depend on UserStore, AuditLogStore,
 * and RBACEngine — all provided by the aggregator's
 * ServerModule (auth & RBAC bindings).
 *
 * No additional user-mgmt-specific bindings are needed.
 */
val userMgmtModule = module {
    // All dependencies (UserStore, AuditLogStore, RBACEngine)
    // are provided by the aggregator's ServerModule
}
