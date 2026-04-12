package com.assistant.e2e.steps

/**
 * Shared mutable state passed between Cucumber step definitions.
 *
 * Cucumber creates a new instance of each step-definition class per scenario,
 * but this singleton survives across classes within the same scenario execution
 * because it is an `object`.  Reset it in a `@Before` hook when needed.
 */
object SharedTestContext {

    /** Base URL — reads from system property set by Gradle, falls back to localhost:8080. */
    val BASE_URL: String = System.getProperty("test.server.baseUrl") ?: "http://localhost:8080"

    /** JWT obtained during the current scenario. */
    var jwtToken: String? = null

    /** Current user role for the scenario (e.g. "Administrator"). */
    var userRole: String? = null

    /** Current user email. */
    var userEmail: String? = null

    /** Active Jira project key. */
    var projectKey: String? = null

    /** Last HTTP response status code. */
    var lastResponseStatus: Int = 0

    /** Last HTTP response body as text. */
    var lastResponseBody: String = ""

    /** Resets all mutable state — call from a @Before hook. */
    fun reset() {
        jwtToken = null
        userRole = null
        userEmail = null
        projectKey = null
        lastResponseStatus = 0
        lastResponseBody = ""
    }
}
