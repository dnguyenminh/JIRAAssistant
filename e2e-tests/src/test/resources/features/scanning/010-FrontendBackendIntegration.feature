@ui
Feature: Frontend-Backend Integration — End-to-End Flow

  As a developer
  I want to verify the full frontend-backend integration flow
  So that I can ensure API calls, navigation, and data binding work end-to-end

  # Validates: Requirements 13.1, 13.2, 13.4, 13.5, 13.6

  Background:
    Given the backend server is running on "http://localhost:8080"

  # ── Authentication Flow ────────────────────────────────────

  @integration @req-13.1
  Scenario: Login stores JWT and user info in session storage
    When the user calls "POST /api/auth/login" with email and password
    Then the response should contain a JWT token
    And the response should contain user info with role and email
    And the JWT token should be stored in session storage key "jira_assistant_jwt"
    And the user role should be stored in session storage key "jira_assistant_role"

  @integration @req-13.1
  Scenario: All authenticated API calls include JWT Bearer token
    Given the user is authenticated with a valid JWT token
    When the Frontend_App makes a GET request to "/api/projects"
    Then the HTTP request should contain header "Authorization" with value starting with "Bearer "

  @integration @req-13.1
  Scenario: Unauthenticated API call returns 401 Unauthorized
    Given the user has no JWT token in session storage
    When the Frontend_App makes a GET request to "/api/projects" without JWT
    Then the backend should respond with HTTP status 401

  # ── Dashboard Data Loading ─────────────────────────────────

  @integration @req-13.1 @req-13.5
  Scenario: Dashboard loads project analysis data via API
    Given the user is authenticated with a valid JWT token
    And the user has selected project "PROJ"
    When the user navigates to "#dashboard"
    Then the Frontend_App should call "GET /api/projects/PROJ/analysis"
    And the request should include the Authorization header with Bearer token
    And the Dashboard should display the "PROJECT AI HEALTH" metric card
    And the Dashboard should display the "ACTIVE KNOWLEDGE NODES" metric card
    And the Dashboard should display the "NEURAL VELOCITY" metric card

  # ── Navigation Flow ────────────────────────────────────────

  @integration @req-13.5
  Scenario: Navigate from Dashboard to Analysis triggers API call
    Given the user is authenticated and on the Dashboard
    When the user clicks the "ANALYSIS DRIFT" button
    Then the browser hash should change to "#analysis"
    And the Frontend_App should call "GET /api/projects/PROJ/analysis"
    And the Analysis page should display 4 metric cards
    And the Analysis page should display the "VELOCITY TREND" section
    And the Analysis page should display the "BOTTLENECK RADAR" section

  @integration @req-13.4
  Scenario: Navigate from Dashboard to Knowledge Graph triggers API call
    Given the user is authenticated and on the Dashboard
    When the user clicks the "VIEW GRAPH" button
    Then the browser hash should change to "#knowledge_graph"
    And the Frontend_App should call "GET /api/graph/PROJ"
    And the Knowledge Graph page should display the SVG graph container

  @integration @req-13.1
  Scenario: Sidebar navigation works for all routes
    Given the user is authenticated with a valid JWT token
    When the user clicks "Relationship Network" in the sidebar
    Then the browser hash should change to "#knowledge_graph"
    And the page title should update to "Knowledge Graph"
    When the user clicks "Project Analysis" in the sidebar
    Then the browser hash should change to "#analysis"
    When the user clicks "Ticket Intelligence" in the sidebar
    Then the browser hash should change to "#ticket_intelligence"
    When the user clicks "Integrations" in the sidebar
    Then the browser hash should change to "#integrations"
    When the user clicks "User Management" in the sidebar
    Then the browser hash should change to "#user_management"
    When the user clicks "Dashboard" in the sidebar
    Then the browser hash should change to "#dashboard"

  # ── Jira Configuration Integration ─────────────────────────

  @integration @req-13.1
  Scenario: Jira config via Integrations page persists and enables API calls
    Given the user is authenticated with role "Administrator"
    And Jira is not configured
    When the user navigates to "#integrations"
    And the user configures Jira with domain "https://myteam.atlassian.net" email "user@example.com" token "valid-token"
    Then the Backend_Server should store encrypted credentials in provider_configs table
    When the user navigates to "#dashboard"
    Then the Frontend_App should call "GET /api/projects/PROJ/analysis" with Jira data

  # ── Error Handling ─────────────────────────────────────────

  @integration @req-13.6
  Scenario: 401 response clears token but does not redirect
    Given the user is on the Knowledge Graph page
    And the API returns 401 for "/api/graph/PROJ"
    Then the JWT token should be cleared from session storage
    But the browser hash should remain "#knowledge_graph"
    And the page should display an empty state message

  @integration @req-13.6
  Scenario: 403 response shows permission denied message
    Given the user is authenticated with role "Reader"
    When the Frontend_App calls an endpoint requiring Administrator role
    Then the backend should respond with HTTP status 403
    And the Frontend_App should display a permission denied message

  @integration @req-13.6
  Scenario: Network error shows connection error message
    Given the backend server is not reachable
    When the Frontend_App attempts to call any API endpoint
    Then the Frontend_App should display "Cannot connect to server" message

  # ── Full User Journey ──────────────────────────────────────

  @integration @req-13.1 @req-13.2 @req-13.5
  Scenario: Full journey — Login → Configure Jira → Dashboard → Analysis
    Given the user authenticates via "POST /api/auth/login"
    And the JWT token is stored in session storage
    When the user navigates to "#integrations"
    And the user configures Jira connection
    And the user navigates to "#dashboard"
    Then the Dashboard should load project data
    When the user clicks "ANALYSIS DRIFT"
    Then the browser hash should change to "#analysis"
    And the Analysis page should display project metrics
    And all API calls should have included the JWT Authorization header
