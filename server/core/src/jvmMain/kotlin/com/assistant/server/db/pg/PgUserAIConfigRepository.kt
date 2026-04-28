package com.assistant.server.db.pg

import com.assistant.chat.InstructionEntry
import com.assistant.chat.RuleEntry
import com.assistant.chat.SkillEntry
import com.assistant.chat.UserAIConfig
import com.assistant.chat.UserAIConfigRepository
import com.assistant.chat.WorkflowEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.sql.DataSource

/** Shared Json instance for UserAIConfig serialization. */
private val configJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * PostgreSQL-backed implementation of [UserAIConfigRepository].
 * JSON serialization for skills, workflow, instructions, rules columns.
 * Requirements: 6.1, 6.2
 */
class PgUserAIConfigRepository(
    private val dataSource: DataSource
) : UserAIConfigRepository {

    override suspend fun findByUserId(userId: String): UserAIConfig? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgUserAIConfigSql.FIND_BY_USER_ID).use { ps ->
                ps.setString(1, userId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgUserAIConfigRepository] findByUserId failed: ${e.message}")
        null
    }

    override suspend fun save(config: UserAIConfig) {
        try {
            val now = Instant.now().toString()
            dataSource.connection.use { conn ->
                conn.prepareStatement(PgUserAIConfigSql.UPSERT).use { ps ->
                    ps.setString(1, config.userId)
                    ps.setString(2, configJson.encodeToString(config.skills))
                    ps.setString(3, configJson.encodeToString(config.workflow))
                    ps.setString(4, configJson.encodeToString(config.instructions))
                    ps.setString(5, configJson.encodeToString(config.rules))
                    ps.setString(6, now)
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            println("[PgUserAIConfigRepository] save failed: ${e.message}")
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): UserAIConfig = UserAIConfig(
        userId = rs.getString("user_id"),
        skills = decodeList(rs.getString("skills_json")),
        workflow = decodeList(rs.getString("workflow_json")),
        instructions = decodeList(rs.getString("instructions_json")),
        rules = decodeList(rs.getString("rules_json")),
        updatedAt = rs.getString("updated_at")
    )

    private inline fun <reified T> decodeList(json: String): List<T> =
        runCatching { configJson.decodeFromString<List<T>>(json) }
            .getOrDefault(emptyList())
}
