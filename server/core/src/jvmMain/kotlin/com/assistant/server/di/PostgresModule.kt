package com.assistant.server.di

import com.assistant.chat.ChatConversationRepository
import com.assistant.chat.ChatRepository
import com.assistant.chat.UserAIConfigRepository
import com.assistant.chat.UserToolPermissionRepository
import com.assistant.kb.KBRepository
import com.assistant.kb.ProviderConfigRepository
import com.assistant.mcp.McpServerRepository
import com.assistant.scan.ScanLogRepository
import com.assistant.scan.ScanStateRepository
import com.assistant.server.config.ServerConfig
import com.assistant.server.db.*
import com.assistant.server.db.pg.*
import com.assistant.settings.SettingsRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import javax.sql.DataSource

/**
 * Koin module for PostgreSQL-backed persistence.
 *
 * Creates HikariCP DataSource, runs Flyway migrations,
 * and wires all Pg* repository implementations.
 */
fun postgresModule(config: ServerConfig): Module = module {
    single<DataSource> { createDataSource() }

    single<KBRepository> { PgKBRepository(get()) }
    single<SettingsRepository> { PgSettingsRepository(get()) }
    single<ProviderConfigRepository> {
        PgProviderConfigRepository(get(), config.encryptionKey)
    }
    single<ScanStateRepository> { PgScanStateRepository(get()) }
    single<ScanLogRepository> { PgScanLogRepository(get()) }
    single<ChatRepository> { PgChatRepository(get()) }
    single<ChatConversationRepository> {
        PgChatConversationRepository(get())
    }
    single<UserAIConfigRepository> { PgUserAIConfigRepository(get()) }
    single<McpServerRepository> { PgMcpServerRepository(get()) }
    single<UserToolPermissionRepository> {
        PgUserToolPermissionRepository(get())
    }
    single<DocumentRepository> { PgDocumentRepository(get()) }
    single<JobRepository> { PgJobRepository(get()) }
}

private fun createDataSource(): DataSource {
    val dbConfig = DatabaseConfig.fromEnvironment()
    val dataSource = DataSourceFactory.create(dbConfig)
    FlywayMigrator.migrate(dataSource)
    return dataSource
}
