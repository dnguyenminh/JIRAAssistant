package com.assistant.jira

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Concrete implementation of the JiraClient using Ktor.
 */
class JiraRestClient(
    private val httpClient: HttpClient,
    private val host: String, // e.g., "https://your-domain.atlassian.net"
    private val authHeader: String // e.g., "Basic {base64(email:token)}" OR "Bearer {token}"
) : JiraClient {

    override suspend fun getProjects(): List<JiraProject> {
        return try {
            val response = httpClient.get("$host/rest/api/3/project") {
                header(HttpHeaders.Authorization, authHeader)
            }
            if (response.status.isSuccess()) {
                response.body<List<JiraProject>>()
            } else {
                println("[JiraRestClient] getProjects: HTTP ${response.status}")
                emptyList()
            }
        } catch (e: Exception) {
            println("[JiraRestClient] getProjects failed: ${e::class.simpleName}: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getIssues(projectKey: String, maxResults: Int): List<JiraIssue> {
        return try {
            val jql = "project=$projectKey ORDER BY created DESC"
            val url = "$host/rest/api/3/search/jql"
            val allIssues = mutableListOf<JiraIssue>()
            var nextPageToken: String? = null
            val pageSize = minOf(maxResults, 100) // Jira Cloud max 100 per page

            do {
                val response = httpClient.get(url) {
                    header(HttpHeaders.Authorization, authHeader)
                    parameter("jql", jql)
                    parameter("maxResults", pageSize)
                    parameter("fields", "summary,status,resolution,created,updated,description,parent,subtasks,issuelinks,attachment,issuetype")
                    if (nextPageToken != null) parameter("nextPageToken", nextPageToken)
                }

                if (!response.status.isSuccess()) {
                    println("[JiraRestClient] getIssues: HTTP ${response.status} for $projectKey")
                    break
                }

                val body = response.bodyAsText()
                val jsonObj = Json.decodeFromString<JsonObject>(body)
                val issuesArray = jsonObj["issues"]
                if (issuesArray != null) {
                    val page = Json { ignoreUnknownKeys = true }.decodeFromJsonElement<List<JiraIssue>>(issuesArray)
                    allIssues.addAll(page)
                }

                val isLast = jsonObj["isLast"]?.let {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
                } ?: true
                nextPageToken = jsonObj["nextPageToken"]?.let {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                }

                if (allIssues.size >= maxResults) break
            } while (!isLast && nextPageToken != null)

            println("[JiraRestClient] getIssues: fetched ${allIssues.size} total issues for $projectKey")
            allIssues.take(maxResults)
        } catch (e: Exception) {
            println("[JiraRestClient] getIssues failed for $projectKey: ${e::class.simpleName}: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? {
        return try {
            val response = httpClient.get("$host/rest/api/3/issue/$issueKey") {
                header(HttpHeaders.Authorization, authHeader)
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                Json { ignoreUnknownKeys = true }.decodeFromString<JiraIssue>(body)
            } else {
                println("[JiraRestClient] getIssueDetails: HTTP ${response.status} for $issueKey")
                null
            }
        } catch (e: Exception) {
            println("[JiraRestClient] getIssueDetails failed for $issueKey: ${e::class.simpleName}: ${e.message}")
            null
        }
    }
}
