package com.assistant.server.db

/**
 * PostgreSQL connection configuration read from environment variables.
 *
 * Defaults match the design document:
 * - DATABASE_USER → "postgres"
 * - DATABASE_POOL_SIZE → 10
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int,
    val connectionTimeout: Long
) {
    companion object {
        private const val DEFAULT_USER = "postgres"
        private const val DEFAULT_POOL_SIZE = 10
        private const val DEFAULT_CONNECTION_TIMEOUT = 30_000L

        fun fromEnvironment(): DatabaseConfig = fromEnvMap(System.getenv())

        /**
         * Build config from an arbitrary env map — useful for testing.
         */
        fun fromEnvMap(env: Map<String, String>): DatabaseConfig = DatabaseConfig(
            jdbcUrl = env["DATABASE_URL"] ?: "",
            username = env["DATABASE_USER"] ?: DEFAULT_USER,
            password = env["DATABASE_PASSWORD"] ?: "",
            maxPoolSize = env["DATABASE_POOL_SIZE"]?.toIntOrNull()
                ?: DEFAULT_POOL_SIZE,
            connectionTimeout = DEFAULT_CONNECTION_TIMEOUT
        )
    }
}
