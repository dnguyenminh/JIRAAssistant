@api
Feature: AI-Powered Ticket Analysis — Backend Orchestration

  As a Scrum Master
  I want the assistant to analyze ticket relationships using AI
  So that I can see functional clusters and dependencies

  # Validates: Requirements 12.1–12.6

  Background:
    Given the backend server is running
    And Jira is configured with valid credentials

  @req-12.1
  Scenario: AI Orchestrator uses KB-First strategy
    Given ticket "PROJ-42" has a cached analysis in the Knowledge Base
    When a GET request is made to "/api/analysis/PROJ-42"
    Then the response should return the cached KB result
    And no AI provider should be called
    And the response should contain requirementSummary, evolutionHistory, and complexityAssessment

  @req-12.1
  Scenario: AI Orchestrator calls AI provider on KB miss
    Given ticket "PROJ-99" has no cached analysis in the Knowledge Base
    When a GET request is made to "/api/analysis/PROJ-99"
    Then the AI_Orchestrator should call the highest-priority active AI provider
    And the result should be saved to the Knowledge Base
    And the response should contain the analysis result

  @req-12.2
  Scenario: Force re-analyze bypasses KB cache
    Given ticket "PROJ-42" has a cached analysis in the Knowledge Base
    When a POST request is made to "/api/analysis/PROJ-42/reanalyze"
    Then the AI_Orchestrator should call an AI provider regardless of cache
    And the Knowledge Base record should be overwritten with the new result

  @req-12.3
  Scenario: AI provider failover on timeout
    Given the primary AI provider (Ollama) is offline
    And the secondary AI provider (Gemini) is active
    When a GET request is made to "/api/analysis/PROJ-50"
    Then the AI_Orchestrator should failover to Gemini after 30s timeout
    And the failover event should be logged

  @req-12.4
  Scenario: Extracting semantic relationships between tickets
    Given the project "PROJ" has tickets "PROJ-1" and "PROJ-2"
    When a GET request is made to "/api/graph/PROJ"
    Then the response should contain nodes for both tickets
    And the response should contain edges representing relationships
    And semantic relationships should be marked with type "SEMANTIC"

  @req-12.5
  Scenario: AI response parsing produces structured result
    When the AI_Orchestrator receives a valid AI response
    Then it should parse the response into RequirementSummary, EvolutionHistory, and ComplexityAssessment
    And the Scrum point should be within the valid scale: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40

  @req-12.6
  Scenario: AI retry on invalid JSON response
    Given the AI provider returns invalid JSON on first attempt
    When the AI_Orchestrator processes the response
    Then it should retry up to 2 times with adjusted prompt
    And if all retries fail, it should return an error message to the user
