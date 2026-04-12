@ui
Feature: Integrations — AI Provider & Jira Configuration Management

  As an Administrator
  I want to manage and test all provider connections including Jira
  So that the system always has available data sources and AI providers

  # Validates: Requirements 6.1–6.17

  Background:
    Given the user is authenticated with role "Administrator"
    And the user navigates to the Integrations page

  # ── Provider Cards Layout ──────────────────────────────────

  @req-6.1
  Scenario: Provider cards are displayed in a responsive grid
    Then the page should display 5 provider cards in a grid layout
    And the cards should be: Jira Cloud Services, Ollama (Local), Google Gemini API, LM Studio, Gemini CLI Interface
    And the grid should be responsive with minimum 380px per card

  @req-6.2
  Scenario: Each provider card shows required elements
    Then each provider card should display a provider logo/icon
    And each provider card should display a status dot
    And each provider card should display the provider name and type
    And each provider card should display a priority number
    And each provider card should have "TEST LINK" and "CONFIGURE" buttons

  @req-6.3
  Scenario: Status dots reflect connection state with tooltips
    Then an Active provider should show a green status dot
    And a Standby provider should show a blue status dot
    And an Offline provider should show a red status dot
    When the user hovers over a status dot
    Then a tooltip should appear with connection details

  # ── Jira Configuration ─────────────────────────────────────

  @req-6.6
  Scenario: Jira CONFIGURE button opens dedicated config modal
    When the user clicks "CONFIGURE" on the Jira Cloud Services card
    Then a modal should appear with title "Jira Cloud Services"
    And the modal should contain a "JIRA DOMAIN URL" text input
    And the modal should contain an "EMAIL / SERVICE ACCOUNT" email input
    And the modal should contain an "API TOKEN" password input
    And the modal should contain a "SAVE & TEST" button
    And the modal should contain a close button

  @req-6.6
  Scenario: Jira API token field has visibility toggle
    Given the Jira config modal is open
    When the user clicks the eye toggle button next to the API TOKEN field
    Then the API TOKEN field should change from password to text type
    When the user clicks the eye toggle button again
    Then the API TOKEN field should change back to password type

  @req-6.6
  Scenario: Jira SAVE & TEST validates required fields
    Given the Jira config modal is open
    And the "JIRA DOMAIN URL" field is empty
    When the user clicks "SAVE & TEST"
    Then an error message "All fields are required" should be displayed
    And no API call should be made

  @req-6.14
  Scenario: Jira SAVE & TEST with valid credentials succeeds
    Given the Jira config modal is open
    And the user enters domain "https://myteam.atlassian.net"
    And the user enters email "user@example.com"
    And the user enters API token "valid-token-123"
    When the user clicks "SAVE & TEST"
    Then the button text should change to "SAVING..."
    And a progress bar should animate
    And the Backend_Server should call "PUT /api/integrations/jira/config"
    And the Backend_Server should validate credentials against Jira API
    And the Jira card status dot should change to Active (green)
    And a success toast "Jira configuration saved successfully" should appear
    And the modal should close after 1.5 seconds

  @req-6.14
  Scenario: Jira SAVE & TEST with invalid credentials shows error
    Given the Jira config modal is open
    And the user enters domain "https://invalid.atlassian.net"
    And the user enters email "user@example.com"
    And the user enters API token "bad-token"
    When the user clicks "SAVE & TEST"
    Then the Backend_Server should return status "offline" with an error message
    And the Jira card status dot should remain Offline (red)
    And an error message should be displayed in the modal status area
    And an error toast should appear

  @req-6.17
  Scenario: Jira credentials persist across page navigation
    Given Jira is configured with domain "https://myteam.atlassian.net"
    When the user navigates to Dashboard and back to Integrations
    Then the Jira card should show status Active
    And the Jira config modal should pre-fill the domain field

  @req-6.6
  Scenario: Jira config modal closes on overlay click
    Given the Jira config modal is open
    When the user clicks outside the modal content area
    Then the modal should close

  @req-6.6
  Scenario: Jira config modal closes on close button click
    Given the Jira config modal is open
    When the user clicks the close button
    Then the modal should close

  # ── AI Provider Configuration ──────────────────────────────

  @req-6.10
  Scenario: Ollama CONFIGURE opens config modal with correct fields
    When the user clicks "CONFIGURE" on the Ollama card
    Then a config modal should appear with fields: Endpoint URL, Model Name, Temperature slider, Max Tokens
    And the modal should have a "SAVE & TEST" button

  @req-6.11
  Scenario: Gemini CONFIGURE opens config modal with correct fields
    When the user clicks "CONFIGURE" on the Google Gemini API card
    Then a config modal should appear with fields: API Key (password), Model Tier (dropdown), Temperature slider, Max Tokens
    And the Model Tier dropdown should include "Gemini 1.5 Pro", "Gemini 1.0 Ultra", "Gemini 1.5 Flash"

  @req-6.12
  Scenario: LM Studio CONFIGURE opens config modal with correct fields
    When the user clicks "CONFIGURE" on the LM Studio card
    Then a config modal should appear with fields: Endpoint URL, Model Name, Temperature slider, Max Tokens

  @req-6.13
  Scenario: Gemini CLI CONFIGURE opens config modal with correct fields
    When the user clicks "CONFIGURE" on the Gemini CLI Interface card
    Then a config modal should appear with fields: CLI Path, Model Name

  # ── TEST LINK ──────────────────────────────────────────────

  @req-6.4 @req-6.5
  Scenario: TEST LINK checks provider connectivity with progress
    When the user clicks "TEST LINK" on the Ollama provider card
    Then the button text should change to "PROBING..."
    And the button should be disabled during testing
    And a progress bar should animate from 0% to 100%
    And the Backend_Server should call "POST /api/integrations/ollama/test"
    And the result should update the status dot
    And the button should reset to "TEST LINK" after completion

  @req-6.4
  Scenario: TEST LINK timeout returns result within 5 seconds
    When the user clicks "TEST LINK" on any provider card
    Then the test should complete within 5 seconds

  # ── Failover Priority ──────────────────────────────────────

  @req-6.16
  Scenario: Administrator can reorder provider priority with arrow buttons
    Given the providers are displayed in priority order
    When the user clicks the down arrow on the first provider
    Then the first and second providers should swap positions
    And the priority numbers should update accordingly
    And the Backend_Server should persist the new order

  # ── RBAC ───────────────────────────────────────────────────

  @req-6.8
  Scenario: Non-Administrator can view but not edit provider configuration
    Given the user is authenticated with role "Neural_Architect"
    And the user navigates to the Integrations page
    Then the provider cards should be visible with all information
    But the "CONFIGURE" buttons should be disabled (opacity 0.5, pointer-events none)
    And the priority arrow buttons should be disabled
    And the "TEST LINK" buttons should remain enabled

  @req-6.8
  Scenario: Reader can view but not edit provider configuration
    Given the user is authenticated with role "Reader"
    And the user navigates to the Integrations page
    Then the provider cards should be visible
    But the "CONFIGURE" buttons should be disabled
    And the priority arrow buttons should be disabled

  # ── Error Handling ─────────────────────────────────────────

  Scenario: Integrations page shows default providers when API returns 401
    Given the user has no valid JWT token
    When the user navigates to the Integrations page
    Then the page should display 5 default provider cards
    And the cards should show Standby/Offline status
    And the page should not redirect to another route

  Scenario: Integrations page shows default providers when backend is unreachable
    Given the backend server is not running
    When the user navigates to the Integrations page
    Then the page should display 5 default provider cards as fallback
