package com.assistant.jira

import org.koin.dsl.module

/**
 * Shared Koin module for Jira connectivity.
 *
 * Provides a default [NoOpJiraClient] singleton that returns empty results.
 * On the server side, [ServerModule] overrides this with a real [JiraRestClient]
 * when Jira credentials are configured in the database.
 */
val jiraModule = module {
    factory<JiraClient> { NoOpJiraClient() }
}
