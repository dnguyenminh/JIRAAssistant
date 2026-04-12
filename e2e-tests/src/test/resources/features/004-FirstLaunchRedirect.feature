@ui
Feature: First-Launch Redirect — Jira Configuration Check

  As a user opening the application for the first time
  I want to be guided to configure Jira connection
  So that the system can access project data

  # Replaces old 004-Onboarding.feature
  # Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6

  Background:
    Given the backend server is running

  @req-1.1
  Scenario: First launch redirects Administrator to Integrations when Jira not configured
    Given Jira is not configured in the database
    And the user is authenticated with role "Administrator"
    When the user opens the application at the root URL
    Then the Frontend_App should call "GET /api/integrations/jira/status"
    And the response should contain "configured: false"
    And the browser hash should change to "#integrations"
    And the Integrations page should be displayed within the Shell

  @req-1.3
  Scenario: First launch shows toast for non-Administrator when Jira not configured
    Given Jira is not configured in the database
    And the user is authenticated with role "Reader"
    When the user opens the application at the root URL
    Then the Frontend_App should call "GET /api/integrations/jira/status"
    And a toast notification should appear with text "Please ask an administrator to configure Jira"
    And the browser hash should change to "#dashboard"
    And the Dashboard should display with empty data placeholders

  @req-1.4
  Scenario: Application navigates to Dashboard when Jira is already configured
    Given Jira is configured in the database with domain "https://myteam.atlassian.net"
    And the user is authenticated with role "Administrator"
    When the user opens the application at the root URL
    Then the browser hash should change to "#dashboard"
    And the Dashboard page should be displayed

  @req-1.5
  Scenario: JWT token is created for user session
    Given the user has a valid JWT token in session storage
    Then the token should contain claims: user_id, email, role
    And the token should have a 24-hour expiry

  @req-1.6
  Scenario: Backend uses Jira credentials from database for all API calls
    Given Jira is configured in the database with domain "https://myteam.atlassian.net"
    When the Frontend_App calls "GET /api/projects"
    Then the Backend_Server should use Jira credentials from the provider_configs table
    And the request to Jira API should use Basic Auth with stored email and API token

  @req-1.1
  Scenario: Jira status endpoint is public and does not require JWT
    When an unauthenticated request is made to "GET /api/integrations/jira/status"
    Then the response status should be 200
    And the response should contain "configured" field

  Scenario: Direct navigation to a specific page works when authenticated
    Given the user is authenticated with role "Administrator"
    When the user navigates directly to "#knowledge_graph"
    Then the Knowledge Graph page should be displayed
    And the sidebar should highlight "Relationship Network" as active
