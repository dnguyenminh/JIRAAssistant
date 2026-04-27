package com.assistant.jira

import kotlinx.serialization.Serializable

/**
 * Jira changelog models for tracking field change history.
 * Returned when issue is fetched with expand=changelog.
 * Requirements: 16.7
 */

@Serializable
data class JiraChangelog(
    val histories: List<JiraChangeHistory> = emptyList(),
    val total: Int = 0
)

@Serializable
data class JiraChangeHistory(
    val id: String = "",
    val author: JiraUser? = null,
    val created: String? = null,
    val items: List<JiraChangeItem> = emptyList()
)

@Serializable
data class JiraChangeItem(
    val field: String = "",
    val fromString: String? = null,
    val toString: String? = null
)
