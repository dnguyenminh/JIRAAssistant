package com.assistant.server.di

import com.assistant.auth.AuthService
import com.assistant.auth.UserRole
import com.assistant.rbac.*
import com.assistant.server.auth.AuthServiceImpl
import com.assistant.server.config.ServerConfig
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for core platform services: config, auth, RBAC,
 * database infrastructure, and middleware bindings.
 *
 * This module is the foundation that all other sub-modules depend on.
 */
fun coreModule(config: ServerConfig): Module = module {
    single { config }

    // PostgreSQL persistence (DataSource, Flyway, all Pg* repositories)
    includes(postgresModule(config))

    // RBAC
    single<AuditLogStore> { FileBasedAuditLogStore("data") }
    single<UserStore> {
        InMemoryUserStore().also { store ->
            runBlocking {
                store.addUser(User(id = "admin", name = "admin", email = "admin@assistant.local", role = UserRole.ADMINISTRATOR))
                store.addUser(User(id = "user", name = "user", email = "user@assistant.local", role = UserRole.READER))
            }
        }
    }
    single<RBACEngine> { RBACEngineImpl(get(), get()) }

    // Auth (depends on UserStore for registering authenticated users)
    single<AuthService> { AuthServiceImpl(get(), get(), get(), get()) }
}
