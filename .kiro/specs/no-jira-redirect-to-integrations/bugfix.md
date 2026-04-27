# Bugfix Requirements Document

## Introduction

After login, when no project key is stored in sessionStorage and Jira credentials have not been configured, the user is navigated to the Project Select page (`#project_select`). The backend returns an empty project list (via `NoOpJiraClient`), and the page displays "No projects match your search" with no way to navigate to the Integrations page to configure Jira. The user is effectively stuck.

The root cause is that `AppStartup.checkAuthAndNavigate()` sends users to `project_select` without checking Jira configuration status first. The existing `checkJiraStatusAndNavigate()` logic (which correctly redirects to Integrations when Jira is unconfigured) is never reached when no project key is stored. Additionally, `ProjectSelectPage` itself does not detect the "empty because Jira not configured" scenario and offers no escape route.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a logged-in user has no project key in sessionStorage AND Jira credentials are not configured THEN the system navigates to `#project_select` which displays "No projects match your search" with no option to configure Jira or navigate away

1.2 WHEN the Project Select page loads and receives an empty project list from `/api/projects` because Jira is not configured THEN the system shows a generic empty message ("No projects match your search.") without explaining the cause or providing an action to resolve it

1.3 WHEN a non-administrator user reaches the Project Select page with no projects because Jira is not configured THEN the system provides no guidance that an administrator needs to configure Jira credentials

### Expected Behavior (Correct)

2.1 WHEN a logged-in Administrator has no project key in sessionStorage AND Jira credentials are not configured THEN the system SHALL automatically redirect to `#integrations` so the admin can configure Jira credentials

2.2 WHEN a logged-in non-Administrator has no project key in sessionStorage AND Jira credentials are not configured THEN the system SHALL display the Project Select page with a Jira disconnect icon in the footer area AND show a popup/modal informing the user to contact an administrator to configure Jira

2.3 WHEN the Project Select page detects empty projects AND Jira is not configured (checked via `/api/integrations/jira/status`) THEN for Administrators the system SHALL redirect to `#integrations`, and for non-Administrators the system SHALL show a disconnect indicator + popup message requesting admin assistance

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a logged-in user has a valid project key in sessionStorage AND Jira is configured THEN the system SHALL CONTINUE TO navigate to the dashboard or handle the current route normally

3.2 WHEN a logged-in user has no project key in sessionStorage AND Jira IS configured (projects are available) THEN the system SHALL CONTINUE TO navigate to `#project_select` and display the list of available projects for selection

3.3 WHEN the Project Select page loads and receives an empty project list because the user's search filter matches no projects (but Jira IS configured) THEN the system SHALL CONTINUE TO display "No projects match your search." as the empty state message

3.4 WHEN a logged-in user has a valid project key AND navigates to a specific hash route THEN the system SHALL CONTINUE TO handle that route directly without redirecting

3.5 WHEN the Jira status check API call fails (network error, server error) THEN the system SHALL CONTINUE TO fall back gracefully (navigate to project_select or dashboard) rather than getting stuck in an error state


---

## Bug Condition Derivation

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type AppState { token: String?, projectKey: String?, jiraConfigured: Boolean }
  OUTPUT: boolean

  // Bug triggers when user is authenticated, has no project selected, and Jira is not configured
  RETURN X.token IS NOT NULL
     AND (X.projectKey IS NULL OR X.projectKey IS BLANK)
     AND X.jiraConfigured = false
END FUNCTION
```

### Property Specification — Fix Checking

```pascal
// Property: Fix Checking — Jira-unconfigured users are not stuck on Project Select
FOR ALL X WHERE isBugCondition(X) DO
  result ← navigateAfterAuth'(X)
  IF X.userRole = ADMINISTRATOR THEN
    ASSERT result.navigatedTo = "integrations"
  ELSE
    ASSERT result.showsDisconnectIcon = true
       AND result.showsPopupMessage = true
       AND result.popupMessage CONTAINS "administrator"
  END IF
  ASSERT result.userIsNotStuck = true
END FOR
```

### Preservation Goal

```pascal
// Property: Preservation Checking — Existing navigation flows unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT navigateAfterAuth(X) = navigateAfterAuth'(X)
END FOR
```

This ensures:
- **F** (original): `checkAuthAndNavigate()` sends users with no project key directly to `project_select` without checking Jira status
- **F'** (fixed): `checkAuthAndNavigate()` checks Jira status when no project key exists, and `ProjectSelectPage` detects unconfigured Jira and provides an escape route
- For all non-buggy inputs (Jira configured, or project key exists), behavior is identical
