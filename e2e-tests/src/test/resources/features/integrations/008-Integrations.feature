@ui
Feature: Integrations — AI Provider Configuration Management

  As an Administrator
  I want to manage and test all AI provider connections
  So that the system always has available AI providers

  # Validates: Requirements 6.1–6.17

  Background:
    Given the user is authenticated with role "Administrator"
    And the user navigates to the Integrations page

  # ── Provider Cards Layout ──────────────────────────────────

  @req-6.1
  Scenario: Provider cards are displayed in a responsive grid
    Then the page should display 7 provider cards in a grid layout
    And the cards should be: Ollama (Local), Google Gemini API, LM Studio, Gemini CLI Interface, Copilot CLI (GitHub), Kiro CLI (Amazon), Embedding Model
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
    Then the page should display 7 default provider cards
    And the cards should show Standby/Offline status
    And the page should not redirect to another route

  Scenario: Integrations page shows default providers when backend is unreachable
    Given the backend server is not running
    When the user navigates to the Integrations page
    Then the page should display 7 default provider cards as fallback
