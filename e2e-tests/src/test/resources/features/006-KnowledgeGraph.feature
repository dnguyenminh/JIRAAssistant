@ui
Feature: Knowledge Graph — Ticket Relationship Network

  As a Product Owner
  I want to see a visual graph of ticket relationships
  So that I can quickly identify feature clusters and hidden dependencies

  # Validates: Requirements 3.1–3.10

  Background:
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"
    And the user navigates to the Knowledge Graph page

  @req-3.1
  Scenario: Graph renders nodes with ticket key labels
    Then the graph SVG container should be visible
    And the graph should display ticket nodes as circles
    And each node should be labeled with its ticket key

  @req-3.2
  Scenario: Nodes are color-coded by type
    Then Feature nodes should be colored Cyan (#2dfecf)
    And Dependency nodes should be colored Blue (#3386ff)
    And UI Module nodes should be colored Violet (#be9dff)

  @req-3.3
  Scenario: Edges distinguish explicit and semantic relationships
    Then solid edges should represent explicit Jira relationships
    And dashed edges should represent AI-detected semantic relationships

  @req-3.4
  Scenario: Hovering a node highlights it
    When the user hovers over a ticket node
    Then the node should scale to 1.1x
    And the node should display a white border highlight
    When the user moves the mouse away
    Then the node should return to normal scale

  @req-3.5
  Scenario: Clicking a node opens the detail panel
    When the user clicks on a ticket node "PROJ-10"
    Then a 300px detail panel should appear on the right side
    And the panel should display the Ticket Key "PROJ-10"
    And the panel should display the Summary
    And the panel should display the Description
    And the panel should have an "OPEN IN JIRA" button linking to the Jira ticket

  @req-3.5
  Scenario: Detail panel closes when close button is clicked
    Given the detail panel is open for node "PROJ-10"
    When the user clicks the close button on the detail panel
    Then the detail panel should be hidden

  @req-3.6
  Scenario: Search filter narrows displayed nodes by key
    Given the graph displays multiple nodes
    When the user types "auth" in the search input
    Then only nodes whose key contains "auth" should be fully visible
    And non-matching nodes should have reduced opacity

  @req-3.6
  Scenario: Search filter narrows displayed nodes by summary
    Given the graph displays multiple nodes
    When the user types "payment" in the search input
    Then only nodes whose summary contains "payment" should be fully visible

  @req-3.6
  Scenario: Clearing search filter shows all nodes
    Given the search input contains "auth"
    When the user clears the search input
    Then all nodes should be fully visible

  @req-3.8
  Scenario: Graph supports zoom via mouse wheel
    When the user scrolls the mouse wheel up on the graph
    Then the graph should zoom in (viewBox shrinks)
    When the user scrolls the mouse wheel down on the graph
    Then the graph should zoom out (viewBox expands)

  @req-3.8
  Scenario: Graph supports pan via mouse drag
    When the user clicks and drags on the graph canvas
    Then the graph should pan in the drag direction
    And the cursor should change to "grabbing" during drag

  @req-3.9
  Scenario: Graph renders within performance threshold
    Given the project has up to 100 tickets
    Then the graph should render all nodes within 3 seconds

  @req-3.10
  Scenario: Clusters are visually distinguished
    Given the graph contains 2 or more feature clusters
    Then each cluster should have a distinct background color
    And each cluster should have a surrounding boundary rectangle
    And cluster labels should be displayed in uppercase

  Scenario: Graph shows empty state when no data available
    Given the project has no tickets
    When the user navigates to the Knowledge Graph page
    Then the graph should display an empty state message
    And the message should indicate no graph data is available

  Scenario: Graph handles API error gracefully
    Given the API returns 401 for "/api/graph/PROJ"
    When the user navigates to the Knowledge Graph page
    Then the graph should display an empty state message
    And the page should not redirect to another route
