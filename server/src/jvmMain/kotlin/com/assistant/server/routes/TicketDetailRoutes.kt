package com.assistant.server.routes

import com.assistant.graph.LinkedTicketDTO
import com.assistant.graph.SubTaskDTO
import com.assistant.jira.JiraClient
import com.assistant.jira.JiraCredentialsService
import com.assistant.jira.JiraIssueLink
import com.assistant.jira.JiraRestClient
import com.assistant.jira.JiraSubtask
import com.assistant.jira.NoOpJiraClient
import io.ktor.client.HttpClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.Base64

/**
 * Ticket detail routes — linked tickets and sub-tasks.
 * Requirements: 20.3, 20.4, 20.5, 20.6
 */
fun Routing.ticketDetailRoutes() {
    val credentialsService by inject<JiraCredentialsService>()
    val httpClient by inject<HttpClient>()

    route("/api/projects/{key}/tickets/{ticketKey}") {
        get("/links") {
            val ticketKey = call.parameters["ticketKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketKey is required"))

            val jiraClient = createJiraClient(credentialsService, httpClient)
            val links = fetchLinkedTickets(jiraClient, ticketKey)
            call.respond(HttpStatusCode.OK, links)
        }

        get("/subtasks") {
            val ticketKey = call.parameters["ticketKey"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketKey is required"))

            val jiraClient = createJiraClient(credentialsService, httpClient)
            val subtasks = fetchSubTasks(jiraClient, ticketKey)
            call.respond(HttpStatusCode.OK, subtasks)
        }
    }
}

/**
 * Fetch linked tickets from Jira and map to LinkedTicketDTO list.
 * Returns empty list if Jira unavailable or no links found.
 */
internal suspend fun fetchLinkedTickets(
    jiraClient: JiraClient,
    ticketKey: String
): List<LinkedTicketDTO> {
    return try {
        if (jiraClient is NoOpJiraClient) return emptyList()
        val issue = jiraClient.getIssueDetails(ticketKey) ?: return emptyList()
        val issueLinks = issue.fields.issuelinks ?: return emptyList()
        issueLinks.flatMap { link -> mapIssueLinkToDtos(link, ticketKey) }
    } catch (e: Exception) {
        println("[TicketDetailRoutes] fetchLinkedTickets failed for $ticketKey: ${e.message}")
        emptyList()
    }
}

/**
 * Map a single JiraIssueLink to LinkedTicketDTO(s).
 * Each link has an inward or outward issue (or both in rare cases).
 */
internal fun mapIssueLinkToDtos(link: JiraIssueLink, currentKey: String): List<LinkedTicketDTO> {
    val results = mutableListOf<LinkedTicketDTO>()
    val linkType = link.type

    link.outwardIssue?.let { outward ->
        if (outward.key != currentKey) {
            results += LinkedTicketDTO(
                key = outward.key,
                summary = outward.fields?.summary ?: outward.key,
                relationship = linkType?.outward ?: linkType?.name ?: "relates to"
            )
        }
    }
    link.inwardIssue?.let { inward ->
        if (inward.key != currentKey) {
            results += LinkedTicketDTO(
                key = inward.key,
                summary = inward.fields?.summary ?: inward.key,
                relationship = linkType?.inward ?: linkType?.name ?: "relates to"
            )
        }
    }
    return results
}

private fun createJiraClient(
    credentialsService: JiraCredentialsService,
    httpClient: HttpClient
): JiraClient {
    val credentials = credentialsService.getJiraCredentials() ?: return NoOpJiraClient()
    val token = Base64.getEncoder().encodeToString(
        "${credentials.email}:${credentials.apiToken}".toByteArray()
    )
    return JiraRestClient(httpClient, credentials.domain, "Basic $token")
}

/**
 * Fetch sub-tasks from Jira and map to SubTaskDTO list.
 * Returns empty list if Jira unavailable or no sub-tasks found.
 */
internal suspend fun fetchSubTasks(
    jiraClient: JiraClient,
    ticketKey: String
): List<SubTaskDTO> {
    return try {
        if (jiraClient is NoOpJiraClient) return emptyList()
        val issue = jiraClient.getIssueDetails(ticketKey) ?: return emptyList()
        val subtasks = issue.fields.subtasks ?: return emptyList()
        subtasks.mapNotNull { mapSubTaskToDto(it) }
    } catch (e: Exception) {
        println("[TicketDetailRoutes] fetchSubTasks failed for $ticketKey: ${e.message}")
        emptyList()
    }
}

/** Map a JiraSubtask to SubTaskDTO, returning null if key is blank. */
internal fun mapSubTaskToDto(subtask: JiraSubtask): SubTaskDTO? {
    if (subtask.key.isBlank()) return null
    return SubTaskDTO(
        key = subtask.key,
        summary = subtask.fields?.summary ?: subtask.key,
        status = subtask.fields?.status?.name ?: "Unknown"
    )
}
