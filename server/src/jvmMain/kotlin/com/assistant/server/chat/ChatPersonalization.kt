package com.assistant.server.chat

import com.assistant.chat.UserAIConfigRepository

/**
 * Build personalization context from user AI config.
 * Extracted from ChatServiceImpl for file size compliance.
 * Requirements: 19.41, 19.42
 */
internal object ChatPersonalization {

    /** Format user skills, workflow, instructions, rules into prompt text. */
    suspend fun build(userId: String, repo: UserAIConfigRepository?): String {
        val config = repo?.findByUserId(userId) ?: return ""
        return buildString {
            if (config.skills.isNotEmpty()) {
                appendLine("Skills: " + config.skills.joinToString(", ") { "${it.name} (${it.level})" })
            }
            if (config.workflow.isNotEmpty()) {
                appendLine("Workflow: " + config.workflow.sortedBy { it.step }.joinToString(" → ") { "${it.step}. ${it.name}" })
            }
            if (config.instructions.isNotEmpty()) {
                val sorted = config.instructions.sortedByDescending { priorityOrder(it.priority) }
                appendLine("Instructions: " + sorted.joinToString("; ") { "[${it.priority}] ${it.instruction}" })
            }
            if (config.rules.isNotEmpty()) {
                appendLine("RULES: " + config.rules.joinToString("; ") { "[${it.type}] ${it.rule}" })
            }
        }
    }

    private fun priorityOrder(priority: String): Int = when (priority) {
        "Cao" -> 3
        "Trung bình" -> 2
        "Thấp" -> 1
        else -> 0
    }
}
