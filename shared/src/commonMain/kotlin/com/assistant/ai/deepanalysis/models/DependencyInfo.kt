package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * A single dependency reference (blocking, related, or external).
 * Requirements: 19.4
 */
@Serializable
data class DependencyItem(
    val key: String = "",
    val summary: String = "",
    val relationshipType: String = "",
    val riskLevel: String = ""
)

/**
 * Dependencies extracted from deep analysis.
 * Contains blocking issues, related issues, and external dependencies.
 * Requirements: 19.4
 */
@Serializable
data class DependencyInfo(
    val blockingIssues: List<DependencyItem> = emptyList(),
    val relatedIssues: List<DependencyItem> = emptyList(),
    val externalDependencies: List<String> = emptyList()
)
