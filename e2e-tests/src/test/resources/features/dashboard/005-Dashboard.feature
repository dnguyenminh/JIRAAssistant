@ui
Feature: Dashboard — Project Overview

  As a project member
  I want to see a quick overview of project health, AI metrics and system activity
  So that I can understand the project status immediately after logging in

  # Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7

  Background:
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"
    And the user is on the Dashboard page

  @req-2.1
  Scenario: Dashboard displays three hero metric cards
    Then the Dashboard should display the "PROJECT AI HEALTH" card with a percentage and delta
    And the Dashboard should display the "ACTIVE KNOWLEDGE NODES" card with count and total
    And the Dashboard should display the "NEURAL VELOCITY" card with score and status

  @req-2.2
  Scenario: Dashboard displays Relationship Network preview with navigation
    Then the Dashboard should display a Relationship Network preview card
    And the preview card should contain an SVG element
    And the preview card should have a "VIEW GRAPH" button

  @req-2.3
  Scenario: Dashboard displays AI Estimation Drift chart with navigation
    Then the Dashboard should display an AI Estimation Drift chart card
    And the chart card should contain an SVG element
    And the chart should have an "ANALYSIS DRIFT" button

  @req-2.4
  Scenario: Dashboard displays Neural Console with system logs
    Then the Neural Console should display at least 3 log entries
    And each log entry should include a timestamp in brackets
    And each log entry should include a tag label
    And each log entry should include a message text
    And the tags should include "AI_SYNC", "KB_WRITE", or "HEARTBEAT"

  @req-2.6
  Scenario: Sidebar navigation displays 6 items with active highlight
    Then the sidebar should display 6 navigation items
    And the items should be: Dashboard, Relationship Network, Project Analysis, Ticket Intelligence, Integrations, User Management
    And the "Dashboard" item should be highlighted as active

  @req-2.2
  Scenario: VIEW GRAPH button navigates to Knowledge Graph
    When the user clicks the "VIEW GRAPH" button on the network preview card
    Then the browser hash should change to "#knowledge_graph"
    And the Knowledge Graph page should be displayed
    And the sidebar should highlight "Relationship Network" as active

  @req-2.3
  Scenario: ANALYSIS DRIFT button navigates to Project Analysis
    When the user clicks the "ANALYSIS DRIFT" button on the drift chart
    Then the browser hash should change to "#analysis"
    And the Project Analysis page should be displayed
    And the sidebar should highlight "Project Analysis" as active

  @req-2.5
  Scenario: User avatar dropdown shows account options
    When the user clicks the avatar on the navigation bar
    Then a dropdown should appear with user email and role
    And the dropdown should contain "Account Settings" link
    And the dropdown should contain "Sign Out" link

  @req-2.5
  Scenario: Administrator sees App Settings in dropdown
    Given the user is authenticated with role "Administrator"
    And the user is on the Dashboard page
    When the user clicks the avatar on the navigation bar
    Then the dropdown should contain "App Settings" link

  @req-2.5
  Scenario: Non-Administrator does not see App Settings in dropdown
    Given the user is authenticated with role "Neural_Architect"
    And the user is on the Dashboard page
    When the user clicks the avatar on the navigation bar
    Then the dropdown should not contain "App Settings" link

  @req-2.7
  Scenario: Sign Out clears session and redirects to Dashboard
    Given the user clicks the avatar on the navigation bar
    When the user clicks "Sign Out" from the dropdown
    Then the JWT token should be removed from session storage
    And the user role should be removed from session storage
    And the browser hash should change to "#dashboard"

  Scenario: Dashboard handles API error gracefully
    Given the backend returns 401 for "/api/projects/PROJ/analysis"
    When the user is on the Dashboard page
    Then the Dashboard should still render the page layout
    And the metric cards should display placeholder values
    And the page should not redirect to another route

  Scenario: Navigation between pages preserves sidebar state
    When the user clicks "Relationship Network" in the sidebar
    Then the browser hash should change to "#knowledge_graph"
    And the sidebar should highlight "Relationship Network" as active
    When the user clicks "Dashboard" in the sidebar
    Then the browser hash should change to "#dashboard"
    And the sidebar should highlight "Dashboard" as active
