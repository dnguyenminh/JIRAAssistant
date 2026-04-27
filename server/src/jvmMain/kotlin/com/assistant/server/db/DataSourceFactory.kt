package com.assistant.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Creates a [HikariDataSource] from the given [DatabaseConfig].
 *
 * Pool settings:
 * - maximumPoolSize from config
 * - connectionTimeout from config (default 30 000 ms)
 * - connectionTestQuery = "SELECT 1"
 * - poolName = "jira-assistant-pool"
 */
object DataSourceFactory {

    private const val POOL_NAME = "jira-assistant-pool"
    private const val TEST_QUERY = "SELECT 1"

    fun create(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = buildConfig(config)
        return HikariDataSource(hikariConfig)
    }

    private fun buildConfig(config: DatabaseConfig): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            connectionTimeout = config.connectionTimeout
            connectionTestQuery = TEST_QUERY
            poolName = POOL_NAME
        }
}
