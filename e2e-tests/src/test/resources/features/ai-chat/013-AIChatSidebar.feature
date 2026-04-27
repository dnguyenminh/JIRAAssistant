@ui
Feature: AI Chat Sidebar — Interactive AI Assistant Panel

  As a user
  I want to interact with an AI assistant via a slide-in sidebar
  So that I can ask questions, get suggestions, and execute actions without leaving the current page

  # Validates: Requirements 19.1–19.4, 19.7, 19.8, 19.12–19.15, 19.17, 19.17a, 19.18

  Background:
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"

  # ── Toggle & Visibility ────────────────────────────────────

  @req-19.1 @req-19.4
  Scenario: Page displays AI Chat toggle button at bottom-left
    When the user navigates to "dashboard"
    Then the AI Chat toggle button should be visible

  @req-19.1 @req-19.3
  Scenario: Clicking toggle button opens the AI Chat Sidebar panel
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    Then the AI Chat Sidebar should be open

  @req-19.2
  Scenario: Sidebar displays header and close button
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    Then the sidebar should display header "AI Assistant"
    And the sidebar should display a close button

  @req-19.3
  Scenario: Clicking close button closes the sidebar
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user clicks the chat close button
    Then the AI Chat Sidebar should be closed

  # ── Input & Messaging ─────────────────────────────────────

  @req-19.2
  Scenario: Sidebar displays message input area and send button
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    Then the sidebar should display a chat input area
    And the sidebar should display a send button

  @req-19.7 @req-19.8
  Scenario: User can type a message and send it with Enter key
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user types "Hello AI" in the chat input
    And the user presses Enter in the chat input
    Then a user message bubble should appear in the chat

  @req-19.8
  Scenario: AI response appears as assistant bubble
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "What is this project about?"
    Then an assistant message bubble should appear in the chat

  @req-19.7
  Scenario: Typing indicator shows while waiting for AI response
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "Describe the dashboard"
    Then the typing indicator should appear briefly

  # ── Action Buttons ─────────────────────────────────────────

  @req-19.12
  Scenario: Action buttons render when AI suggests actions
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "Navigate me to integrations"
    Then action buttons should appear in the chat

  @req-19.12
  Scenario: Clicking navigate action changes the page hash
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "Go to integrations page"
    And the user clicks a chat action button
    Then the browser hash should contain "integrations"

  # ── Error Handling ─────────────────────────────────────────

  @req-19.18
  Scenario: Error banner shows when AI provider is not configured
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the AI chat encounters a provider error
    Then the chat error banner should be visible

  @req-19.18
  Scenario: Error banner has link to Integrations page
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the AI chat encounters a provider error
    Then the error banner should contain a link to Integrations

  # ── Command History ────────────────────────────────────────

  @req-19.17a
  Scenario: Command history navigation with arrow keys
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "First message"
    And the user presses ArrowUp in the chat input
    Then the chat input should contain previous message text

  # ── Chat History ───────────────────────────────────────────

  @req-19.17
  Scenario: Chat history loads on first sidebar open
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    Then the chat message area should be present

  # ── RBAC ───────────────────────────────────────────────────

  @req-19.14
  Scenario: Reader cannot execute changeConfig action
    Given the user is authenticated with role "Reader"
    And the user has selected project "PROJ"
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user triggers a changeConfig action in chat
    Then a permission denied message should appear in the chat

  # ── Page Exclusions ────────────────────────────────────────

  @req-19.4
  Scenario: Sidebar is NOT visible on Login page
    When the user opens the login page
    Then the AI Chat toggle button should not be visible

  @req-19.4
  Scenario: Sidebar is NOT visible on Project Select page
    When the user opens the project select page
    Then the AI Chat toggle button should not be visible
