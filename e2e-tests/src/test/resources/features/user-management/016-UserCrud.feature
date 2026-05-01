@ui
Feature: User CRUD & Profile Management

  As an Administrator
  I want to create, view, edit, disable/enable, and delete users
  So that I can manage the team's access to the system

  # Validates: BRD Req 1-5, FSD UC-01 through UC-05

  Background:
    Given the user is authenticated with role "Administrator"
    And the user navigates to the User Management page

  # ── Create ─────────────────────────────────────────────────

  @req-1.1
  Scenario: E2E-UI-01 — Create user with valid data
    Given the admin is on the User Management page
    When the admin clicks the "Add User" button
    And the admin fills in name "Test User E2E" and email "e2e-test@example.com"
    And the admin selects role "NEURAL_ARCHITECT"
    And the admin clicks the "Create" button
    Then the creation form should close
    And the user "Test User E2E" should appear in the User Directory
    And the user should have role "NEURAL_ARCHITECT" and status "ACTIVE"

  @req-1.2
  Scenario: E2E-UI-02 — Form validation rejects empty name
    Given the admin has opened the "Add User" form
    When the admin leaves the name field empty
    And the admin enters email "valid@test.com"
    And the admin clicks the "Create" button
    Then an error message "Name is required" should be displayed

  # ── View ───────────────────────────────────────────────────

  @req-2.1
  Scenario: E2E-UI-03 — View user detail panel
    Given the User Directory contains at least one user
    When the admin clicks on a user row
    Then the Detail Panel should display the user's name
    And the Detail Panel should display the user's email
    And the Detail Panel should display the user's role
    And the Detail Panel should display a status badge
    And the Detail Panel should show Edit, Disable, and Delete buttons

  # ── Edit ───────────────────────────────────────────────────

  @req-3.1
  Scenario: E2E-UI-04 — Edit user name successfully
    Given the admin has opened the Detail Panel for a user
    When the admin clicks the "Edit" button
    And the admin changes the name to "Edited Name E2E"
    And the admin clicks the "Save" button
    Then the Detail Panel should show name "Edited Name E2E"
    And the User Directory should show "Edited Name E2E" for that user

  @req-3.9
  Scenario: E2E-UI-05 — Cancel edit reverts to original values
    Given the admin has opened the Detail Panel for a user
    When the admin clicks the "Edit" button
    And the admin changes the name to "Should Not Save"
    And the admin clicks the "Cancel" button
    Then the Detail Panel should not show name "Should Not Save"

  # ── Disable / Enable ──────────────────────────────────────

  @req-4.1
  Scenario: E2E-UI-06 — Disable an active user
    Given the admin has opened the Detail Panel for an ACTIVE user
    When the admin clicks the "Disable" button
    And the confirmation dialog appears
    And the admin confirms the action
    Then the user's status badge should show "DISABLED"

  @req-4.6
  Scenario: E2E-UI-07 — Enable a disabled user
    Given the admin has opened the Detail Panel for a DISABLED user
    When the admin clicks the "Enable" button
    Then the user's status badge should show "ACTIVE"

  # ── Delete ─────────────────────────────────────────────────

  @req-5.1
  Scenario: E2E-UI-08 — Delete user with name confirmation
    Given the admin has opened the Detail Panel for a deletable user
    When the admin clicks the "Delete" button
    And the admin types the user's name in the confirmation input
    And the admin clicks the confirm delete button
    Then the user should be removed from the User Directory

  @req-5.2
  Scenario: E2E-UI-09 — Delete confirm button disabled with wrong name
    Given the admin has opened the delete confirmation dialog
    When the admin types "Wrong Name" in the confirmation input
    Then the confirm delete button should be disabled

  # ── Regression ─────────────────────────────────────────────

  @regression
  Scenario: E2E-UI-10 — User list displays correctly after CRUD
    Given the admin navigates to the User Management page
    Then the User Directory should display pre-seeded users
    And each user row should show avatar, name, email, and role

  @regression
  Scenario: E2E-UI-11 — Existing role change still works
    Given the admin selects a user in the User Directory
    When the admin changes the user's role via the role dropdown
    Then the role should be updated successfully

  @regression
  Scenario: E2E-UI-12 — Existing permission toggle still works
    Given the admin selects a user in the User Directory
    When the admin toggles one of the permissions
    Then the permission state should change
