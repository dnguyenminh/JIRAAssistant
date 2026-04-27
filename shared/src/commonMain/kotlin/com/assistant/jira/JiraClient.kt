package com.assistant.jira

import kotlinx.serialization.Serializable

/**
 * Common models for Jira objects.
 */
@Serializable
data class JiraProject(
    val id: String,
    val key: String,
    val name: String,
    val projectTypeKey: String? = null
)

@Serializable
data class JiraIssue(
    val id: String,
    val key: String,
    val fields: JiraIssueFields,
    val changelog: JiraChangelog? = null
)

@Serializable
data class JiraIssueType(
    val name: String = "",
    val subtask: Boolean = false
)

@Serializable
data class JiraIssueFields(
    val summary: String = "",
    val description: kotlinx.serialization.json.JsonElement? = null,
    val status: JiraStatus? = null,
    val resolution: JiraResolution? = null,
    val issuetype: JiraIssueType? = null,
    val created: String? = null,
    val updated: String? = null,
    val parent: JiraParent? = null,
    val subtasks: List<JiraSubtask>? = null,
    val issuelinks: List<JiraIssueLink>? = null,
    val attachment: List<JiraAttachment>? = null,
    val priority: JiraPriority? = null,
    val assignee: JiraUser? = null,
    val reporter: JiraUser? = null,
    val labels: List<String>? = null,
    val components: List<JiraComponent>? = null,
    val comment: JiraCommentWrapper? = null,
    @kotlinx.serialization.SerialName("customfield_10016")
    val storyPoints: Double? = null
) {
    /** Get description as plain text. Handles both Jira v2 (string) and v3 (ADF JSON). */
    val descriptionText: String
        get() = when {
            description == null -> ""
            description is kotlinx.serialization.json.JsonPrimitive ->
                (description as kotlinx.serialization.json.JsonPrimitive).content
            description is kotlinx.serialization.json.JsonObject -> extractAdfText(description as kotlinx.serialization.json.JsonObject)
            else -> ""
        }

    companion object {
        /** Recursively extract text from Atlassian Document Format (ADF) JSON. */
        fun extractAdfText(node: kotlinx.serialization.json.JsonObject): String {
            val sb = StringBuilder()
            extractAdfTextRecursive(node, sb)
            return sb.toString().trim()
        }

        private fun extractAdfTextRecursive(node: kotlinx.serialization.json.JsonObject, sb: StringBuilder) {
            val type = (node["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val text = (node["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (text != null) sb.append(text)
            if (type == "hardBreak" || type == "paragraph" || type == "heading") {
                if (sb.isNotEmpty() && !sb.endsWith('\n')) sb.append('\n')
            }
            val content = node["content"] as? kotlinx.serialization.json.JsonArray ?: return
            for (child in content) {
                if (child is kotlinx.serialization.json.JsonObject) extractAdfTextRecursive(child, sb)
            }
            if (type == "paragraph" || type == "heading" || type == "bulletList" || type == "orderedList") {
                if (sb.isNotEmpty() && !sb.endsWith('\n')) sb.append('\n')
            }
        }
    }
}

@Serializable
data class JiraParent(
    val id: String = "",
    val key: String = ""
)

@Serializable
data class JiraSubtask(
    val id: String = "",
    val key: String = "",
    val fields: JiraSubtaskFields? = null
)

@Serializable
data class JiraSubtaskFields(
    val summary: String = "",
    val status: JiraStatus? = null
)

@Serializable
data class JiraIssueLink(
    val id: String = "",
    val type: JiraIssueLinkType? = null,
    val inwardIssue: JiraLinkedIssue? = null,
    val outwardIssue: JiraLinkedIssue? = null
)

@Serializable
data class JiraIssueLinkType(
    val name: String = "",
    val inward: String = "",
    val outward: String = ""
)

@Serializable
data class JiraLinkedIssue(
    val id: String = "",
    val key: String = "",
    val fields: JiraLinkedIssueFields? = null
)

@Serializable
data class JiraLinkedIssueFields(
    val summary: String = "",
    val status: JiraStatus? = null
)

@Serializable
data class JiraAttachment(
    val id: String = "",
    val filename: String = "",
    val mimeType: String? = null,
    val size: Long = 0,
    val content: String? = null  // Download URL from Jira API v3
)

@Serializable
data class JiraStatus(
    val name: String = "",
    val id: String = ""
)

@Serializable
data class JiraResolution(
    val name: String = "",
    val id: String = ""
)

/**
 * Interface to interact with Jira API.
 * Follows SOLID principles for easier substitution.
 */
interface JiraClient {
    /**
     * Fetch all available projects for the authenticated user.
     */
    suspend fun getProjects(): List<JiraProject>

    /**
     * Fetch issues for a specific project.
     * Supports basic filtering and projection.
     */
    suspend fun getIssues(projectKey: String, maxResults: Int = 50): List<JiraIssue>

    /**
     * Fetch full details for a specific issue.
     */
    suspend fun getIssueDetails(issueKey: String): JiraIssue?

    /**
     * Fetch comments for an issue with pagination. Req 9.1
     * @param issueKey Jira issue key (e.g., "PROJ-100")
     * @param startAt Starting index (0-based)
     * @param maxResults Max comments per page (default 50)
     * @return JiraCommentPageResponse with pagination metadata
     */
    suspend fun getIssueComments(
        issueKey: String,
        startAt: Int = 0,
        maxResults: Int = 50
    ): JiraCommentPageResponse = JiraCommentPageResponse()
}
