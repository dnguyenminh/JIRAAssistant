package com.assistant.chat

import kotlinx.serialization.Serializable

/**
 * Structured entry for user skill/expertise.
 * Requirements: 19.38
 */
@Serializable
data class SkillEntry(
    val name: String,
    val level: String = "Intermediate",
    val description: String = ""
)

/**
 * Structured entry for workflow step.
 * Requirements: 19.38
 */
@Serializable
data class WorkflowEntry(
    val step: Int,
    val name: String,
    val description: String = ""
)

/**
 * Structured entry for AI instruction.
 * Requirements: 19.38
 */
@Serializable
data class InstructionEntry(
    val instruction: String,
    val priority: String = "Trung bình"
)

/**
 * Structured entry for AI rule/constraint.
 * Requirements: 19.38
 */
@Serializable
data class RuleEntry(
    val rule: String,
    val type: String = "Bắt buộc"
)

/**
 * Per-user AI personalization config with structured entries.
 * Requirements: 19.38, 19.39
 */
@Serializable
data class UserAIConfig(
    val userId: String = "",
    val skills: List<SkillEntry> = emptyList(),
    val workflow: List<WorkflowEntry> = emptyList(),
    val instructions: List<InstructionEntry> = emptyList(),
    val rules: List<RuleEntry> = emptyList(),
    val updatedAt: String = ""
)
