@ui
Feature: MCP Servers — Internal MCP Server "Jira Assistant UI" & External Server Management

  As an Administrator
  I want to manage MCP servers and use the Internal MCP Server tools
  So that AI agents can control the application and extend capabilities with external tools

  # Validates: Requirements 6.20–6.30, 6.47, 6.58, 6.70–6.72, 6.107–6.109

  Background:
    Given the user is authenticated with role "Administrator"
    And the user has selected project "PROJ"

  # ══════════════════════════════════════════════════════════
  # MCP Servers Section on Integrations Page (Req 6.20–6.21)
  # ══════════════════════════════════════════════════════════

  @req-6.20
  Scenario: MCP Servers section is visible on Integrations page
    When the user navigates to "integrations"
    Then the MCP Servers section should be visible

  @req-6.21
  Scenario: MCP server cards are displayed with required elements
    When the user navigates to "integrations"
    Then MCP server cards should be displayed
    And each MCP card should show server name and status dot

  # ══════════════════════════════════════════════════════════
  # Internal MCP Server Card (Req 6.70, 6.72)
  # ══════════════════════════════════════════════════════════

  @req-6.70
  Scenario: Internal MCP Server "Jira Assistant UI" is always present
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" MCP server card should be visible

  @req-6.72
  Scenario: Internal server shows LOCAL badge
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" card should display a "LOCAL" badge

  @req-6.70
  Scenario: Internal server shows RUNNING status
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" card should show active status

  @req-6.72
  Scenario: Internal server hides CONFIGURE and REMOVE buttons
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" card should not have CONFIGURE button
    And the "Jira Assistant UI" card should not have REMOVE button

  @req-6.72
  Scenario: Internal server hides TEST button
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" card should not have TEST button

  @req-6.72
  Scenario: Internal server shows START/STOP toggle
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" card should have a START or STOP button

  # ══════════════════════════════════════════════════════════
  # MCP Server Status Display (Req 6.58)
  # ══════════════════════════════════════════════════════════

  @req-6.58
  Scenario: Status dot colors reflect server state
    When the user navigates to "integrations"
    Then active MCP servers should show green status dot
    And offline MCP servers should show grey status dot

  @req-6.58
  Scenario: MCP card shows tool count
    When the user navigates to "integrations"
    Then the "Jira Assistant UI" card should display tool count

  # ══════════════════════════════════════════════════════════
  # Tools Expandable Section (Req 6.47, 6.107)
  # ══════════════════════════════════════════════════════════

  @req-6.47
  Scenario: Internal server has expandable Tools section
    When the user navigates to "integrations"
    And the user expands the tools section on "Jira Assistant UI" card
    Then the tools list should be visible

  @req-6.47 @req-6.107
  Scenario: Tools list shows tool names and descriptions
    When the user navigates to "integrations"
    And the user expands the tools section on "Jira Assistant UI" card
    Then the tools list should contain "navigate_to_page"
    And the tools list should contain "start_scan"
    And the tools list should contain "analyze_ticket"
    And the tools list should contain "send_chat_message"

  @req-6.47
  Scenario: View Schema button opens tool schema modal
    When the user navigates to "integrations"
    And the user expands the tools section on "Jira Assistant UI" card
    And the user clicks View Schema on a tool
    Then the tool schema modal should be visible
    And the schema modal should display JSON content

  # ══════════════════════════════════════════════════════════
  # Tool Permissions Toggle (Per-User, Req 6.47a replacement)
  # ══════════════════════════════════════════════════════════

  Scenario: Tool permission toggles are visible in tools list
    When the user navigates to "integrations"
    And the user expands the tools section on "Jira Assistant UI" card
    Then tool permission checkboxes should be visible

  Scenario: Enable All button enables all tools
    When the user navigates to "integrations"
    And the user expands the tools section on "Jira Assistant UI" card
    And the user clicks "Enable All" in the tools section
    Then the enabled counter should show all tools enabled

  Scenario: Disable All button disables all tools
    When the user navigates to "integrations"
    And the user expands the tools section on "Jira Assistant UI" card
    And the user clicks "Disable All" in the tools section
    Then the enabled counter should show zero tools enabled

  # ══════════════════════════════════════════════════════════
  # Add MCP Server (Req 6.22–6.24)
  # ══════════════════════════════════════════════════════════

  @req-6.22
  Scenario: Add MCP Server button opens registration form
    When the user navigates to "integrations"
    And the user clicks the Add MCP Server button
    Then the MCP config modal should be visible
    And the modal should contain Server Name field
    And the modal should contain Command field

  @req-6.23
  Scenario: MCP config modal supports Form mode
    When the user navigates to "integrations"
    And the user clicks the Add MCP Server button
    Then the form mode should be active by default
    And the form should have Args field
    And the form should have Environment Variables field

  @req-6.24
  Scenario: Toggle between Form and JSON mode
    When the user navigates to "integrations"
    And the user clicks the Add MCP Server button
    And the user toggles to JSON mode
    Then the JSON editor should be visible
    When the user toggles to Form mode
    Then the form fields should be visible

  # ══════════════════════════════════════════════════════════
  # Import/Export (Req 6.27)
  # ══════════════════════════════════════════════════════════

  @req-6.27
  Scenario: Export button is visible on MCP section
    When the user navigates to "integrations"
    Then the MCP export button should be visible

  @req-6.27
  Scenario: Import button is visible on MCP section
    When the user navigates to "integrations"
    Then the MCP import button should be visible

  # ══════════════════════════════════════════════════════════
  # RBAC (Req 6.30)
  # ══════════════════════════════════════════════════════════

  @req-6.30
  Scenario: Reader can view MCP servers but not manage
    Given the user is authenticated with role "Reader"
    And the user has selected project "PROJ"
    When the user navigates to "integrations"
    Then MCP server cards should be displayed
    But the Add MCP Server button should be hidden or disabled
    And MCP CONFIGURE buttons should be hidden or disabled

  @req-6.30
  Scenario: Neural_Architect can view MCP servers but not manage
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"
    When the user navigates to "integrations"
    Then MCP server cards should be displayed
    But the Add MCP Server button should be hidden or disabled

  # ══════════════════════════════════════════════════════════
  # State Transition Toasts (Req 6.59)
  # ══════════════════════════════════════════════════════════

  @req-6.59
  Scenario: Toast notification on server state change
    When the user navigates to "integrations"
    And an MCP server changes state
    Then a toast notification should appear with server name and state

  # ══════════════════════════════════════════════════════════
  # AI Chat Integration — Tool Execution Display (Req 6.54, 6.109)
  # ══════════════════════════════════════════════════════════

  @req-6.54
  Scenario: AI Chat shows tool execution indicator
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "List all projects"
    Then the chat should show tool execution indicator

  @req-6.109
  Scenario: Internal tools use home icon in chat
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "Navigate to integrations"
    Then internal tool calls should display home icon

  @req-6.54
  Scenario: Tool result displayed in collapsible block
    When the user navigates to "dashboard"
    And the user clicks the AI Chat toggle button
    And the user sends a chat message "What projects are available?"
    Then tool results should appear in collapsible blocks

  # ══════════════════════════════════════════════════════════
  # Page Navigation Persistence (Req 6.58)
  # ══════════════════════════════════════════════════════════

  @req-6.58
  Scenario: MCP server status persists across page navigation
    When the user navigates to "integrations"
    And the "Jira Assistant UI" card should show active status
    And the user navigates to "dashboard"
    And the user navigates to "integrations"
    Then the "Jira Assistant UI" card should show active status
