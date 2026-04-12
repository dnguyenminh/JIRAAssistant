Feature: Security & Error Handling — Cross-cutting Concerns

  As a developer
  I want to verify security measures and error handling across the application
  So that the system is robust and secure

  # Validates: Requirements 10.1–10.6, 16.1–16.5

  # ── JWT Authentication ─────────────────────────────────────

  @api @req-10.1
  Scenario: JWT token contains required claims
    When a user authenticates via "POST /api/auth/login"
    Then the JWT token should contain claims: user_id, email, role
    And the token should have a 24-hour expiry
    And the token should be signed with HMAC256

  @ui @req-10.2
  Scenario: All API requests include JWT Bearer token
    Given the user is authenticated
    When the Frontend_App makes any API request
    Then the request should include header "Authorization: Bearer {token}"

  @api @req-10.3
  Scenario: Valid JWT token grants access to protected endpoints
    Given the user has a valid JWT token
    When a GET request is made to "/api/projects"
    Then the response status should be 200

  @api @req-10.4
  Scenario: Expired JWT token returns 401
    Given the user has an expired JWT token
    When a GET request is made to "/api/projects"
    Then the response status should be 401

  @api @req-10.4
  Scenario: Invalid JWT token returns 401
    Given the user has a malformed JWT token
    When a GET request is made to "/api/projects"
    Then the response status should be 401

  @api @req-10.5
  Scenario: JWT secret is not hardcoded
    Then the JWT secret should be read from environment variable "JWT_SECRET"
    And the JWT secret should not appear in any source code file

  @api @req-10.6
  Scenario: Frontend handles backend connection failure
    Given the backend server is not reachable
    When the Frontend_App attempts to make an API call
    Then the Frontend_App should display a connection error message
    And the application should not crash

  # ── Encryption at Rest ─────────────────────────────────────

  @api @req-16.3
  Scenario: Provider API keys are encrypted in database
    Given an AI provider is configured with API key "secret-key-123"
    When the provider config is saved to the database
    Then the api_key field in provider_configs table should be encrypted (not plaintext)
    And reading the config back should return the decrypted "secret-key-123"

  @api @req-16.3
  Scenario: Jira API token is encrypted in database
    Given Jira is configured with API token "jira-token-456"
    When the Jira config is saved via "PUT /api/integrations/jira/config"
    Then the credentials should be encrypted with AES-256-GCM in provider_configs table

  @ui @req-16.3
  Scenario: JWT tokens are not stored in localStorage
    Given the user is authenticated
    Then the JWT token should be in sessionStorage (not localStorage)
    And the token should be cleared when the browser tab is closed

  # ── Audit Logging ──────────────────────────────────────────

  @api @req-16.4
  Scenario: Login events are recorded in audit log
    When a user successfully authenticates
    Then an audit log entry should be created with action "LOGIN"

  @api @req-16.4
  Scenario: Permission changes are recorded in audit log
    When an Administrator changes a user's role
    Then an audit log entry should be created with action "ROLE_CHANGE"
    And the entry should contain the old role and new role

  @api @req-16.4
  Scenario: Provider configuration changes are recorded in audit log
    When an Administrator saves a provider configuration
    Then an audit log entry should be created with action "PROVIDER_CONFIG"

  # ── Sign Out ───────────────────────────────────────────────

  @ui @req-16.5
  Scenario: Sign Out invalidates session completely
    Given the user is authenticated
    When the user clicks "Sign Out"
    Then the JWT token should be removed from sessionStorage
    And the user role should be removed from sessionStorage
    And the server should invalidate the session
    And subsequent API calls should return 401

  # ── Error Response Handling ────────────────────────────────

  @api
  Scenario: 400 Bad Request shows validation error
    When the Frontend_App sends an invalid request body
    Then the backend should respond with HTTP status 400
    And the response should contain a descriptive error message

  @api
  Scenario: 403 Forbidden shows permission denied
    Given the user is authenticated with role "Reader"
    When the user attempts to access an Administrator-only endpoint
    Then the backend should respond with HTTP status 403

  @api
  Scenario: 404 Not Found shows resource not found
    When a GET request is made to "/api/analysis/NONEXISTENT-999"
    Then the backend should respond with HTTP status 404

  @api
  Scenario: 500 Internal Server Error is handled gracefully
    Given the backend encounters an unexpected error
    Then the response should contain a generic error message
    And the error details should not expose internal stack traces

  # ── Air-gapped Mode ────────────────────────────────────────

  @api @req-16.1
  Scenario: Air-gapped mode uses only local Ollama
    Given the docker-compose is started with air-gapped profile
    Then only the Ollama AI provider should be available
    And no external API calls should be made
    And all data should remain within the local network
