package com.assistant.jira

import kotlinx.serialization.Serializable

/**
 * Extended Jira models for Deep Analysis content extraction.
 * Covers: priority, user, component, comments, and changelog.
 * Requirements: 16.1, 16.6, 16.7
 */

@Serializable
data class JiraPriority(
    val name: String = "",
    val id: String = ""
)

@Serializable
data class JiraUser(
    val displayName: String = "",
    val emailAddress: String? = null,
    val accountId: String? = null
)

@Serializable
data class JiraComponent(
    val id: String = "",
    val name: String = ""
)

/** Wrapper for Jira comment field (fields.comment). */
@Serializable
data class JiraCommentWrapper(
    val comments: List<JiraComment> = emptyList(),
    val total: Int = 0
)

/** Response from Jira REST API comment pagination endpoint. Req 9.1 */
@Serializable
data class JiraCommentPageResponse(
    val startAt: Int = 0,
    val maxResults: Int = 50,
    val total: Int = 0,
    val comments: List<JiraComment> = emptyList()
)

@Serializable
data class JiraComment(
    val id: String = "",
    val author: JiraUser? = null,
    val created: String? = null,
    val updated: String? = null,
    val body: kotlinx.serialization.json.JsonElement? = null
) {
    /** Extract comment body as plain text (handles ADF and string). */
    val bodyText: String
        get() = when {
            body == null -> ""
            body is kotlinx.serialization.json.JsonPrimitive ->
                (body as kotlinx.serialization.json.JsonPrimitive).content
            body is kotlinx.serialization.json.JsonObject ->
                JiraIssueFields.extractAdfText(
                    body as kotlinx.serialization.json.JsonObject
                )
            else -> ""
        }
}
