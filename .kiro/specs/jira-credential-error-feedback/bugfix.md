# Bugfix Requirements Document

## Introduction

When Jira credentials stored in the database fail to decrypt (e.g., due to ENCRYPTION_KEY mismatch), the `/api/projects` endpoint silently returns `200 OK` with an empty list `[]`. The frontend Project Select page displays "No projects available" with a RETRY button but provides no indication that the root cause is a credential/encryption failure. Users have no way to diagnose or resolve the issue without checking server logs.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN Jira credentials fail to decrypt (e.g., ENCRYPTION_KEY mismatch causes "Tag mismatch" error) THEN the system returns HTTP 200 with an empty project list `[]` and no error indicator

1.2 WHEN Jira credentials are not configured (no `jira` entry in provider_configs) THEN the system returns HTTP 200 with an empty project list `[]` indistinguishable from a decrypt failure

1.3 WHEN the frontend receives an empty project list from `/api/projects` AND Jira status check reports `configured: true` THEN the system displays "No projects available" with only a RETRY button and no explanation of the credential/encryption issue

1.4 WHEN `JiraCredentialsService.getJiraCredentials()` returns null due to decrypt failure THEN the system logs the error server-side but provides no error context in the API response to the client

### Expected Behavior (Correct)

2.1 WHEN Jira credentials fail to decrypt (apiKey is null/blank after decrypt attempt) THEN the system SHALL return a response that includes an error indicator distinguishing "credentials invalid/corrupt" from "not configured" (e.g., a status field like `credentialsError: true` or an appropriate HTTP error status)

2.2 WHEN Jira credentials are not configured (no provider config entry exists) THEN the system SHALL return a response that clearly indicates "not configured" status, distinct from "configured but credentials invalid"

2.3 WHEN the frontend detects a credential error from the API response THEN the system SHALL display a clear error message such as "Jira connection failed. Please check your Jira credentials in Integrations settings." with a link/button to navigate to the Integrations page

2.4 WHEN the frontend detects Jira is not configured THEN the system SHALL display a message indicating Jira needs to be set up, with guidance to the Integrations page (existing behavior for admin redirect is acceptable)

### Unchanged Behavior (Regression Prevention)

3.1 WHEN Jira credentials are valid and decrypt successfully THEN the system SHALL CONTINUE TO return HTTP 200 with the list of Jira projects

3.2 WHEN Jira credentials are valid but the Jira API returns an empty project list (user has no projects) THEN the system SHALL CONTINUE TO return HTTP 200 with an empty list and the existing "No projects available" + RETRY UI

3.3 WHEN the user is not authenticated (invalid/expired JWT) THEN the system SHALL CONTINUE TO return HTTP 401 and the frontend SHALL redirect to login

3.4 WHEN Jira credentials are valid and projects load successfully THEN the system SHALL CONTINUE TO display the sortable, searchable, paginated project table

3.5 WHEN Jira is not configured AND user is an Administrator THEN the system SHALL CONTINUE TO redirect to the Integrations page (existing behavior)

---

## Bug Condition

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type JiraCredentialState
  OUTPUT: boolean
  
  // Returns true when credentials exist in DB but fail to decrypt,
  // OR when the distinction between "not configured" and "decrypt failure" is lost
  RETURN X.providerConfigExists = true AND X.decryptedApiKey IS NULL_OR_BLANK
END FUNCTION
```

### Property Specification — Fix Checking

```pascal
// Property: Fix Checking — Credential Error Feedback
FOR ALL X WHERE isBugCondition(X) DO
  response ← getProjects'(X)
  ASSERT response.statusIndicatesError = true
    AND response.errorType = "CREDENTIALS_INVALID"
    AND frontend.displaysErrorMessage(response) = true
    AND frontend.showsIntegrationsLink(response) = true
END FOR
```

### Preservation Goal

```pascal
// Property: Preservation Checking
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT getProjects(X) = getProjects'(X)
END FOR
```

This ensures that for all non-buggy inputs (valid credentials returning projects, valid credentials with empty Jira, unauthenticated users), the fixed code behaves identically to the original.
