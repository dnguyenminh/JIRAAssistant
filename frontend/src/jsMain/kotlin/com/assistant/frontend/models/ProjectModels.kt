package com.assistant.frontend.models

import kotlinx.serialization.Serializable

/**
 * Wrapper response from GET /api/projects.
 * Contains project list and credential state metadata.
 */
@Serializable
data class ProjectsResponse(
    val projects: List<ProjectInfo> = emptyList(),
    val jiraStatus: String = "OK"
)

@Serializable
data class ProjectInfo(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val projectTypeKey: String? = null
)
