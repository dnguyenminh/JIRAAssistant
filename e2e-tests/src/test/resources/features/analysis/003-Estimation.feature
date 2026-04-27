@api
Feature: AI-Powered Scrum Estimation

  As a Developer
  I want the assistant to suggest story points based on AI analysis
  So that my estimations are more consistent and accurate

  # Validates: Requirements 5.6, 14.1

  Background:
    Given the backend server is running
    And Jira is configured with valid credentials

  @req-5.6
  Scenario: Scrum point estimation returns valid Fibonacci-like value
    When a POST request is made to "/api/estimation/estimate" with ticket "PROJ-42"
    Then the response should contain a scrumPoint field
    And the scrumPoint value should be one of: 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40

  @req-5.6
  Scenario: Estimation includes rationale
    When a POST request is made to "/api/estimation/estimate" with ticket "PROJ-42"
    Then the response should contain a rationale field
    And the rationale should explain the estimation reasoning

  @req-5.6
  Scenario: Edge case — NaN input rounds to valid point
    When the ScrumEstimator receives NaN as raw score
    Then it should return 0 as the closest valid point

  @req-5.6
  Scenario: Edge case — Infinity input rounds to valid point
    When the ScrumEstimator receives Infinity as raw score
    Then it should return 40 as the closest valid point

  @req-5.6
  Scenario: Edge case — Negative input rounds to valid point
    When the ScrumEstimator receives -5.0 as raw score
    Then it should return 0 as the closest valid point

  Scenario: Estimation uses historical data for similar tickets
    Given the Knowledge Base contains analysis for similar tickets
    When a POST request is made to "/api/estimation/estimate" with a new ticket
    Then the AI should reference similar historical tickets in the rationale
    And the suggested points should be consistent with similar tickets

  Scenario: Estimation endpoint requires authentication
    When an unauthenticated POST request is made to "/api/estimation/estimate"
    Then the response status should be 401
