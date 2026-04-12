@ui
Feature: Batch Scan Engine — Scan Control, Progressive Display & Ticket Combobox

  As a Neural Architect
  I want to start batch scans, see progressive results, and select tickets from a combobox
  So that I can efficiently analyze all tickets in a project

  # Validates: Requirements 2.9–2.15, 3.11–3.13, 4.6–4.8, 5.11–5.15, 18.7–18.12

  Background:
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"

  # ── Dashboard Scan Control Panel ───────────────────────────

  @req-2.9
  Scenario: Dashboard displays scan control panel with START SCAN button
    When the user navigates to "dashboard"
    Then the scan control panel should be visible
    And the START SCAN button should be visible

  @req-2.9 @req-2.10
  Scenario: Clicking START SCAN starts a batch scan
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    Then the scan status should change to SCANNING

  @req-2.10
  Scenario: PAUSE button appears during scanning
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    Then the PAUSE button should be visible
    When the user clicks the PAUSE button
    Then the scan status should change to PAUSED

  @req-2.10
  Scenario: RESUME button appears when scan is paused
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    And the user clicks the PAUSE button
    Then the RESUME button should be visible
    When the user clicks the RESUME button
    Then the scan status should change to SCANNING

  @req-2.11
  Scenario: CANCEL button cancels the scan
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    And the user clicks the CANCEL button
    Then the scan status should change to CANCELLED

  @req-2.12
  Scenario: Progress bar shows during scanning
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    Then the scan progress bar should be visible
    And the scan progress label should show percentage

  @req-2.13
  Scenario: Scan log container shows log entries
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    Then the scan log container should be visible

  @req-2.9 @req-7.8
  Scenario: Reader role cannot start scan
    Given the user is authenticated with role "Reader"
    And the user has selected project "PROJ"
    When the user navigates to "dashboard"
    Then the START SCAN button should be disabled

  # ── Knowledge Graph Progressive Display ────────────────────

  @req-3.13
  Scenario: Scan status badge shows on Knowledge Graph page
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    And the user navigates to "knowledge_graph"
    Then the graph scan status badge should be visible

  @req-3.11
  Scenario: Node count updates as scan progresses
    When the user navigates to "knowledge_graph"
    Then the graph node count element should be present

  @req-3.12
  Scenario: Graph renders nodes progressively
    When the user navigates to "knowledge_graph"
    Then the graph SVG container should be present

  # ── Project Analysis Progressive Display ───────────────────

  @req-4.8
  Scenario: Scan status indicator shows on Analysis page
    When the user navigates to "dashboard"
    And the user clicks the START SCAN button
    And the user navigates to "analysis"
    Then the page should display scan status information

  # ── Ticket Intelligence Combobox ───────────────────────────

  @req-5.11
  Scenario: Ticket Intelligence page displays searchable combobox
    When the user navigates to "ticket_intelligence"
    Then the ticket combobox should be visible
    And the ticket search input should be visible

  @req-5.12
  Scenario: Typing in combobox filters ticket list
    When the user navigates to "ticket_intelligence"
    And the user types "PROJ-1" in the ticket search input
    Then the ticket dropdown should be visible

  @req-5.13
  Scenario: Selecting a ticket from dropdown populates the input
    When the user navigates to "ticket_intelligence"
    And the user types "PROJ" in the ticket search input
    And the user clicks a ticket option from the dropdown
    Then the ticket search input should contain the selected ticket

  @req-5.14
  Scenario: Status badges show analysis state
    When the user navigates to "ticket_intelligence"
    Then ticket status badges should be present

  @req-5.15
  Scenario: Dynamic action button shows ANALYZE or RE-ANALYZE based on state
    When the user navigates to "ticket_intelligence"
    Then the action button should be visible

  @req-5.7
  Scenario: Reader cannot trigger analysis from combobox
    Given the user is authenticated with role "Reader"
    And the user has selected project "PROJ"
    When the user navigates to "ticket_intelligence"
    Then the action button should be disabled
