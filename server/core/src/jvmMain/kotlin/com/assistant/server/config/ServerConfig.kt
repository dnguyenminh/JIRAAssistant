package com.assistant.server.config

import com.assistant.settings.SettingsRepository
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm

data class ServerConfig(
    val jwtSecret: String,
    val encryptionKey: String,
    val port: Int,
    val staticDir: String
) {
    fun jwtAlgorithm(): Algorithm = Algorithm.HMAC256(jwtSecret)

    fun jwtVerifier(): JWTVerifier = JWT.require(jwtAlgorithm())
        .withIssuer(JWT_ISSUER)
        .build()

    companion object {
        const val JWT_ISSUER = "jira-assistant"
        const val JWT_AUDIENCE = "jira-assistant-users"
        const val JWT_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours

        /**
         * Bootstrap load — reads only from environment variables.
         * Used at startup before the database is ready.
         */
        fun load(): ServerConfig = ServerConfig(
            jwtSecret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production",
            encryptionKey = System.getenv("ENCRYPTION_KEY") ?: "dev-encryption-key-change-in-production",
            port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
            staticDir = System.getenv("STATIC_DIR") ?: "./static"
        )

        /**
         * Load config from DB with env-var fallback.
         * DB values take priority; if a key is missing in DB, falls back to System.getenv().
         * staticDir always comes from env vars (not configurable via UI).
         */
        suspend fun loadFromDb(settingsRepo: SettingsRepository): ServerConfig = ServerConfig(
            jwtSecret = settingsRepo.get("JWT_SECRET")
                ?: System.getenv("JWT_SECRET")
                ?: "dev-secret-change-in-production",
            encryptionKey = settingsRepo.get("ENCRYPTION_KEY")
                ?: System.getenv("ENCRYPTION_KEY")
                ?: "dev-encryption-key-change-in-production",
            port = (settingsRepo.get("PORT")?.toIntOrNull())
                ?: System.getenv("PORT")?.toIntOrNull()
                ?: 8080,
            staticDir = System.getenv("STATIC_DIR") ?: "./static"
        )
    }
}
