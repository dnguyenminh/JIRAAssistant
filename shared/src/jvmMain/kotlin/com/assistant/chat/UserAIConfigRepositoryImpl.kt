package com.assistant.chat

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Shared Json instance for UserAIConfig serialization. */
private val configJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * JVM implementation of [UserAIConfigRepository].
 * Persists structured entries as JSON arrays in SQLDelight columns.
 * Requirements: 19.39, 19.40
 */
class UserAIConfigRepositoryImpl(
    private val database: JiraDatabase
) : UserAIConfigRepository {

    override suspend fun findByUserId(userId: String): UserAIConfig? {
        return database.knowledgeBaseQueries
            .findUserAIConfig(userId)
            .executeAsOneOrNull()
            ?.let { row ->
                UserAIConfig(
                    userId = row.user_id,
                    skills = decodeSkills(row.skills_json),
                    workflow = decodeWorkflow(row.workflow_json),
                    instructions = decodeInstructions(row.instructions_json),
                    rules = decodeRules(row.rules_json),
                    updatedAt = row.updated_at
                )
            }
    }

    override suspend fun save(config: UserAIConfig) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.upsertUserAIConfig(
            user_id = config.userId,
            skills_json = configJson.encodeToString(config.skills),
            workflow_json = configJson.encodeToString(config.workflow),
            instructions_json = configJson.encodeToString(config.instructions),
            rules_json = configJson.encodeToString(config.rules),
            updated_at = now
        )
    }

    private fun decodeSkills(json: String): List<SkillEntry> =
        runCatching { configJson.decodeFromString<List<SkillEntry>>(json) }
            .getOrDefault(emptyList())

    private fun decodeWorkflow(json: String): List<WorkflowEntry> =
        runCatching { configJson.decodeFromString<List<WorkflowEntry>>(json) }
            .getOrDefault(emptyList())

    private fun decodeInstructions(json: String): List<InstructionEntry> =
        runCatching { configJson.decodeFromString<List<InstructionEntry>>(json) }
            .getOrDefault(emptyList())

    private fun decodeRules(json: String): List<RuleEntry> =
        runCatching { configJson.decodeFromString<List<RuleEntry>>(json) }
            .getOrDefault(emptyList())
}
