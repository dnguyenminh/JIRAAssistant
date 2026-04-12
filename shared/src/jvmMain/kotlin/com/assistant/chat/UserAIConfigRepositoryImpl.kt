package com.assistant.chat

import com.assistant.db.JiraDatabase
import kotlinx.datetime.Clock

/**
 * JVM implementation of [UserAIConfigRepository].
 * Requirements: 19.39, 19.40
 */
class UserAIConfigRepositoryImpl(
    private val database: JiraDatabase
) : UserAIConfigRepository {

    override suspend fun findByUserId(userId: String): UserAIConfig? {
        return database.knowledgeBaseQueries
            .findUserAIConfig(userId)
            .executeAsOneOrNull()
            ?.let {
                UserAIConfig(
                    userId = it.user_id,
                    skills = it.skills,
                    workflow = it.workflow,
                    instructions = it.instructions,
                    rules = it.rules,
                    updatedAt = it.updated_at
                )
            }
    }

    override suspend fun save(config: UserAIConfig) {
        val now = Clock.System.now().toString()
        database.knowledgeBaseQueries.upsertUserAIConfig(
            user_id = config.userId,
            skills = config.skills,
            workflow = config.workflow,
            instructions = config.instructions,
            rules = config.rules,
            updated_at = now
        )
    }
}
