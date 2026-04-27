package com.assistant.server.db.pg

import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.kb.ProviderConfigRepository
import com.assistant.security.CryptoUtils
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL-backed ProviderConfigRepository with encryption at rest.
 * The api_key field is encrypted using AES-256-GCM before writing
 * and decrypted when reading. Extends [ProviderConfigRepository]
 * so it can be injected wherever the base type is expected.
 * Requirements: 6.1, 6.2
 */
class PgProviderConfigRepository(
    private val dataSource: DataSource,
    private val pgEncryptionKey: String
) : ProviderConfigRepository() {

    override fun getAllProviders(): List<ProviderConfig> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgProviderConfigSql.SELECT_ALL).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }
    } catch (e: Exception) {
        println("[PgProviderConfigRepo] getAllProviders failed: ${e.message}")
        emptyList()
    }

    override fun findById(providerId: String): ProviderConfig? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgProviderConfigSql.SELECT_BY_ID).use { ps ->
                ps.setString(1, providerId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgProviderConfigRepo] findById failed: ${e.message}")
        null
    }

    override fun save(config: ProviderConfig): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgProviderConfigSql.UPSERT).use { ps ->
                bindUpsertParams(ps, config)
                ps.executeUpdate() > 0
            }
        }
    } catch (e: Exception) {
        println("[PgProviderConfigRepo] save failed: ${e.message}")
        false
    }

    override fun findByType(type: ProviderType): ProviderConfig? =
        getAllProviders().firstOrNull { it.type == type }

    override fun existsByType(type: ProviderType): Boolean =
        getAllProviders().any { it.type == type }

    override fun updateStatus(providerId: String, status: ConnectionStatus) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgProviderConfigSql.UPDATE_STATUS).use { ps ->
                    ps.setString(1, status.name)
                    ps.setString(2, providerId)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgProviderConfigRepo] updateStatus failed: ${e.message}")
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun bindUpsertParams(
        ps: java.sql.PreparedStatement,
        config: ProviderConfig
    ) {
        val encKey = config.apiKey?.let { encryptApiKey(it) }
        ps.setString(1, config.providerId)
        ps.setString(2, config.name)
        ps.setString(3, config.type.name)
        ps.setString(4, config.endpoint)
        ps.setString(5, encKey)
        ps.setString(6, config.model)
        ps.setInt(7, config.priority)
        ps.setString(8, config.status.name)
        ps.setString(9, config.name)
        ps.setString(10, config.type.name)
        ps.setString(11, config.endpoint)
        ps.setString(12, encKey)
        ps.setString(13, config.model)
        ps.setInt(14, config.priority)
        ps.setString(15, config.status.name)
    }

    private fun mapRow(rs: ResultSet): ProviderConfig = ProviderConfig(
        providerId = rs.getString("provider_id"),
        name = rs.getString("name"),
        type = ProviderType.valueOf(rs.getString("type")),
        endpoint = rs.getString("endpoint"),
        apiKey = rs.getString("api_key")?.let { decryptApiKey(it) },
        model = rs.getString("model"),
        priority = rs.getInt("priority"),
        status = ConnectionStatus.valueOf(rs.getString("status"))
    )

    private fun encryptApiKey(plaintext: String): String =
        CryptoUtils.encryptAES256GCM(plaintext, pgEncryptionKey)

    private fun decryptApiKey(ciphertext: String): String = try {
        CryptoUtils.decryptAES256GCM(ciphertext, pgEncryptionKey)
    } catch (e: Exception) {
        println("[PgProviderConfigRepo] decrypt failed: ${e.message}")
        ""
    }
}
