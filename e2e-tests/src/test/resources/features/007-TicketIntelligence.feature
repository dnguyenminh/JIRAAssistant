@ui
Feature: Ticket Intelligence — AI-Powered Ticket Analysis UI

  As a Scrum Master
  I want to analyze tickets using AI to get requirement summaries, change history and Scrum point estimates
  So that I can deeply understand tickets before sprint planning

  # Validates: Requirements 5.1–5.10

  Background:
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"
    And the user navigates to the Ticket Intelligence page

  # ── Core Analysis Flow ─────────────────────────────────────

  @req-5.1
  Scenario: Page displays ticket input field and analyze button
    Then the page should display a text input for Ticket ID
    And the page should display an "ANALYZE DATA" button
    And the progress section should be hidden
    And the results section should be hidden

  @req-5.1 @req-5.3
  Scenario: Analyze a ticket shows progress and results
    Given the user enters Ticket ID "PROJ-42" in the input field
    When the user clicks the "ANALYZE DATA" button
    Then the progress section should become visible
    And the progress bar should animate from 0% to 100%
    And the status ticker should show phase descriptions
    When the analysis completes
    Then the results section should become visible with 3 tabs

  @req-5.4
  Scenario: Context tab displays requirement summary
    Given the analysis result is displayed for "PROJ-42"
    When the "Summary" tab is active
    Then the tab should display the ticket key and summary
    And the tab should list affected modules

  @req-5.4
  Scenario: Estimation tab displays Scrum point
    Given the analysis result is displayed for "PROJ-42"
    When the user clicks the "Estimation" tab
    Then the tab should display the Scrum Point value
    And the tab should display the estimation rationale

  @req-5.4
  Scenario: Relations tab displays related tickets
    Given the analysis result is displayed for "PROJ-42"
    When the user clicks the "Relations" tab
    Then the tab should display a list of related tickets

  # ── Tab Switching ──────────────────────────────────────────

  Scenario: Tab switching highlights active tab
    Given the analysis result is displayed
    When the user clicks the "Estimation" tab button
    Then the "Estimation" tab button should have the active class
    And the "Summary" tab button should not have the active class
    And the estimation tab content should be visible
    And the summary tab content should be hidden

  # ── KB-First Strategy ──────────────────────────────────────

  @req-5.2
  Scenario: KB-First cache hit returns stored result without calling AI
    Given ticket "PROJ-42" has been previously analyzed and cached in the Knowledge Base
    When the user enters Ticket ID "PROJ-42" and clicks "ANALYZE DATA"
    Then the system should return the cached result from the Knowledge Base
    And no AI agent should be invoked

  @req-5.5
  Scenario: Re-analyze overwrites cached result with fresh AI analysis
    Given ticket "PROJ-42" has a cached result in the Knowledge Base
    When the user clicks the "RE-ANALYZE" button
    Then the AI_Orchestrator should perform a fresh AI analysis
    And the Knowledge Base record should be overwritten with the new result

  # ── Scrum Point Validation ─────────────────────────────────

  @req-5.6
  Scenario: Scrum points are within the valid Fibonacci-like scale
    When the analysis completes for any ticket
    Then the Scrum Point value should be one of: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40

  # ── RBAC ───────────────────────────────────────────────────

  @req-5.7
  Scenario: Reader role cannot trigger analysis
    Given the user is authenticated with role "Reader"
    And the user navigates to the Ticket Intelligence page
    Then the "ANALYZE DATA" button should be disabled
    And the button should have opacity 0.5 and pointer-events none

  @req-5.7
  Scenario: Neural_Architect role can trigger analysis
    Given the user is authenticated with role "Neural_Architect"
    And the user navigates to the Ticket Intelligence page
    Then the "ANALYZE DATA" button should be enabled and clickable

  # ── Long-Running Analysis Polling ──────────────────────────

  @req-13.3
  Scenario: Long-running analysis triggers status polling
    Given the analysis for "PROJ-99" takes longer than 15 seconds
    When the user clicks "ANALYZE DATA"
    Then the Frontend_App should start polling "/api/analysis/PROJ-99/status" every 3 seconds
    And the progress bar should update based on the polling response
    When the analysis completes
    Then the polling should stop
    And the results should be displayed

  # ── Error Handling ─────────────────────────────────────────

  Scenario: Analysis handles API error gracefully
    Given the backend returns an error for "/api/analysis/PROJ-42"
    When the user clicks "ANALYZE DATA"
    Then the progress bar should complete
    And an error message should be displayed
    And the user should be able to retry

  @req-5.10
  Scenario: AI retry on invalid response
    Given the AI provider returns invalid JSON
    When the analysis is triggered
    Then the AI_Orchestrator should retry up to 2 times
    And if all retries fail, an error message should be shown to the user

  Scenario: Empty ticket ID shows validation error
    Given the ticket ID input is empty
    When the user clicks "ANALYZE DATA"
    Then the system should not make an API call
    And a validation message should indicate ticket ID is required
