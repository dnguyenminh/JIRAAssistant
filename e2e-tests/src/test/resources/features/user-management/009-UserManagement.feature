@ui
Feature: User Management — RBAC & Permissions

  As an Administrator
  I want to manage user roles and granular permissions
  So that I can strictly control who can perform which actions in the system

  # Validates: Requirements 7.1–7.9, 11.1–11.5

  # ── Access & Navigation ────────────────────────────────────

  @req-7.9
  Scenario: User Management is accessible via Navbar dropdown
    Given the user is authenticated with role "Administrator"
    When the user clicks the avatar on the navigation bar
    And the user clicks "Account Settings" from the dropdown
    Then the browser hash should change to "#user_management"
    And the User Management page should be displayed

  @req-7.9
  Scenario: User Management is NOT in the sidebar
    Given the user is authenticated with role "Administrator"
    Then the sidebar should not contain a "User Management" direct link
    And User Management should only be accessible via the Navbar dropdown

  # ── User List ──────────────────────────────────────────────

  @req-7.1
  Scenario: User list displays avatar, name, email and role
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page
    Then the page should display a list of users
    And each user row should show a 48px avatar, name, email, and a role dropdown selector

  # ── Role Management ────────────────────────────────────────

  @req-7.2 @req-7.3
  Scenario: Administrator changes a user role
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page
    And user "alice@example.com" currently has the role "Reader"
    When the Administrator selects "Neural_Architect" from the role dropdown for "alice@example.com"
    Then the RBAC_Engine should apply the role change immediately
    And user "alice@example.com" should now have the role "Neural_Architect"
    And the Backend_Server should call "PUT /api/users/{userId}/role"

  # ── Permission Toggles ────────────────────────────────────

  @req-7.4
  Scenario: Permission panel displays 4 toggles
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page
    Then the permission panel should display 4 toggles
    And the toggles should be: Trigger AI Scan, Knowledge Base Write, Update Integrations, Export Neural Data

  @req-7.5
  Scenario: Permission toggle updates with sync indicator
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page
    And the permission panel is displayed for a user
    When the Administrator toggles "Knowledge Base Write" on
    Then the sync indicator should display "IAM SYNC: UPDATING..."
    And a progress bar should animate
    And the sync indicator should display "SYNC COMPLETE" when finished
    And the Backend_Server should call "PUT /api/users/{userId}/permissions"

  # ── Audit Log ──────────────────────────────────────────────

  @req-7.6
  Scenario: Role changes are recorded in audit log
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page
    When the Administrator changes the role of a user
    Then the audit log should contain an entry with actor, target user, old role, new role, and timestamp

  @req-7.7
  Scenario: Neural Console displays audit log entries
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page
    Then the Neural Console at the bottom should display recent audit log entries
    And each entry should include a timestamp, a tag, and a change description
    And the tags should include "IAM_SYNC" or "USER_LOGIN"

  # ── RBAC Access Control ────────────────────────────────────

  @req-7.8 @req-11.3
  Scenario: Non-Administrator is denied access to User Management
    Given the user is authenticated with role "Neural_Architect"
    When the user navigates to "#user_management"
    Then the page should display "Access Denied" message
    And the user list should not be visible

  @req-7.8 @req-11.3
  Scenario: Reader is denied access to User Management
    Given the user is authenticated with role "Reader"
    When the user navigates to "#user_management"
    Then the page should display "Access Denied" message
    And the user list should not be visible

  # ── Permission Matrix ──────────────────────────────────────

  @req-11.1
  Scenario: Administrator role has all permissions
    Then the "Administrator" role should have permissions: VIEW_DASHBOARD, VIEW_GRAPH, VIEW_ANALYSIS, ANALYZE_AI, VIEW_KB, RE_ANALYZE, CONFIG_INTEGRATIONS, TEST_PROVIDER, MANAGE_USERS, TOGGLE_PERMISSIONS, MANAGE_SETTINGS, SIGN_OUT

  @req-11.1
  Scenario: Neural_Architect role has limited permissions
    Then the "Neural_Architect" role should have permissions: VIEW_DASHBOARD, VIEW_GRAPH, VIEW_ANALYSIS, ANALYZE_AI, VIEW_KB, RE_ANALYZE, TEST_PROVIDER, SIGN_OUT
    And the "Neural_Architect" role should NOT have: CONFIG_INTEGRATIONS, MANAGE_USERS, MANAGE_SETTINGS

  @req-11.1
  Scenario: Reader role has view-only permissions
    Then the "Reader" role should have permissions: VIEW_DASHBOARD, VIEW_GRAPH, VIEW_ANALYSIS, VIEW_KB, SIGN_OUT
    And the "Reader" role should NOT have: ANALYZE_AI, RE_ANALYZE, CONFIG_INTEGRATIONS, MANAGE_USERS

  @req-11.2
  Scenario: Reader cannot call AI analysis endpoint
    Given the user is authenticated with role "Reader"
    When the Frontend_App calls "GET /api/analysis/PROJ-42"
    Then the backend should respond with HTTP status 403

  @req-11.4
  Scenario: RBAC is enforced on both server and client side
    Given the user is authenticated with role "Reader"
    Then the "ANALYZE DATA" button on Ticket Intelligence should be disabled (client-side)
    And a direct API call to "/api/analysis/PROJ-42" should return 403 (server-side)

  @req-11.5
  Scenario: Role change takes effect immediately
    Given user "bob@example.com" has role "Reader"
    When the Administrator changes the role to "Neural_Architect"
    Then the next API request from "bob@example.com" should use the new role permissions
