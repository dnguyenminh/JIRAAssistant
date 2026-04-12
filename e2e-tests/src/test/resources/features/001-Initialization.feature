Feature: Application Initialization — Startup & Health Check

  As a developer
  I want to verify the application starts correctly
  So that I can ensure all services are operational

  # Validates: Requirements 8.1, 15.1

  @api @req-8.1
  Scenario: Backend health endpoint returns service status
    Given the backend server is running
    When a GET request is made to "/health"
    Then the response status should be 200
    And the response should contain Jira API connection status
    And the response should contain AI provider status
    And the response should contain Knowledge Base status

  @ui @req-8.1
  Scenario: Frontend loads and initializes router
    Given the backend server is running
    When the user opens the application at the root URL
    Then the page title should contain "Jira Assistant"
    And the living-void background should be rendered
    And the app-container should be present in the DOM
    And the console should log "[App] Jira Assistant Frontend initialized"

  @ui
  Scenario: Frontend renders Shell with sidebar and navbar
    Given the user is authenticated
    When the user opens the application
    Then the sidebar should be visible with navigation items
    And the navbar should be visible with breadcrumb
    And the content area should be present

  @ui
  Scenario: Application handles backend unavailability gracefully
    Given the backend server is not running
    When the user opens the application
    Then the Frontend_App should render the page layout
    And pages should display empty states or error messages
    And the application should not crash or show a blank page

  @api @req-15.1
  Scenario: Static files are served correctly
    Given the backend server is running
    When a GET request is made to "/index.html"
    Then the response should contain the SPA entry point HTML
    And CSS files should be loadable
    And JavaScript bundle should be loadable
