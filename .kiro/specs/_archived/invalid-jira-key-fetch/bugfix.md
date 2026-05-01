# Bugfix Requirements Document

## Introduction

The system treats arbitrary strings (e.g., "active-jobs", "documents") from Jira issue links, sub-tasks, and parent ticket fields as valid Jira issue keys and attempts to fetch them via the Jira REST API. This results in HTTP 404 errors and `IllegalStateException`, which causes the entire prompt build to fail for the affected ticket.

The root cause is that `JiraContentExtractorImpl.collectLinkedKeys()` gathers keys from `issueLinks`, `subTasks`, and `parentKey` without validating them against the Jira ticket key format (`[A-Z][A-Z0-9]+-\d+`). A `TICKET_PATTERN` regex already exists in `RelatedTicketCollector.kt` but is only applied to comment mentions — not to keys from structured fields.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a linked issue key is a non-Jira-format string (e.g., "active-jobs", "documents") THEN the system passes it to `JiraRestClient.getIssueDetails()`, resulting in an HTTP 404 error

1.2 WHEN a sub-task key is a non-Jira-format string THEN the system passes it to `JiraRestClient.getIssueDetails()`, resulting in an HTTP 404 error

1.3 WHEN a parent ticket key is a non-Jira-format string THEN the system passes it to `JiraRestClient.getIssueDetails()`, resulting in an HTTP 404 error

1.4 WHEN `fetchOneLinked()` receives an invalid key and the API returns null THEN the error is silently swallowed, but unnecessary network calls are still made

1.5 WHEN `fetchIssue()` is called with an invalid key for the primary ticket THEN the system throws `IllegalStateException("Ticket X not found in Jira")`, failing the entire prompt build

### Expected Behavior (Correct)

2.1 WHEN a linked issue key is a non-Jira-format string THEN the system SHALL filter it out before making any API call and log a warning indicating the invalid key was skipped

2.2 WHEN a sub-task key is a non-Jira-format string THEN the system SHALL filter it out before making any API call and log a warning indicating the invalid key was skipped

2.3 WHEN a parent ticket key is a non-Jira-format string THEN the system SHALL filter it out before making any API call and log a warning indicating the invalid key was skipped

2.4 WHEN all linked/sub-task/parent keys are invalid THEN the system SHALL return an empty list of linked ticket contents without throwing any exception

2.5 WHEN `fetchIssue()` is called with the primary ticket key THEN the system SHALL validate the key format before making the API call and throw `IllegalArgumentException` with a descriptive message if the key is invalid

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a linked issue key matches the Jira format (e.g., "ICL2-15", "PROJ-123") THEN the system SHALL CONTINUE TO fetch the linked ticket details via the API

3.2 WHEN a sub-task key matches the Jira format THEN the system SHALL CONTINUE TO fetch the sub-task details via the API

3.3 WHEN a parent ticket key matches the Jira format THEN the system SHALL CONTINUE TO fetch the parent ticket details via the API

3.4 WHEN `RelatedTicketCollector` extracts ticket keys from comments using `TICKET_PATTERN` THEN the system SHALL CONTINUE TO collect and validate those keys as before

3.5 WHEN `JiraFieldMappers.mapIssueLinks()` and `mapSubTasks()` map Jira API models to domain models THEN the system SHALL CONTINUE TO map all fields correctly regardless of key validity (validation happens downstream)

---

## Bug Condition (Formal)

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type String (a ticket key candidate)
  OUTPUT: boolean

  // Returns true when the key does NOT match the Jira ticket format
  RETURN NOT matches(X, "[A-Z][A-Z0-9]+-\d+")
END FUNCTION
```

### Fix Checking

```pascal
// Property: Fix Checking — Invalid keys are filtered before API calls
FOR ALL X WHERE isBugCondition(X) DO
  result ← collectLinkedKeys'(base).map { it.first }
  ASSERT X NOT IN result
  ASSERT no_api_call_made_for(X)
END FOR
```

### Preservation Checking

```pascal
// Property: Preservation Checking — Valid keys still fetched normally
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT collectLinkedKeys(base) = collectLinkedKeys'(base)  // same keys collected
  ASSERT fetchOneLinked(client, X, type) = fetchOneLinked'(client, X, type)  // same behavior
END FOR
```
