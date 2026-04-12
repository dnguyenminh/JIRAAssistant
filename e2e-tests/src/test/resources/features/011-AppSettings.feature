@ui
Feature: App Settings — Server Configuration Management

  As an Administrator
  I want to manage server configuration through the UI
  So that I can update settings without restarting the server

  # Validates: Requirements 8.1, 8.2, 8.3, 11.1, 16.1

  Background:
    Given the user is authenticated with role "Administrator"

  @req-8.1
  Scenario: Settings page is accessible from Navbar dropdown
    When the user clicks the avatar on the navigation bar
    Then the dropdown should contain "App Settings" link
    When the user clicks "App Settings"
    Then the browser hash should change to "#settings"
    And the Settings page should be displayed

  @req-8.1
  Scenario: Settings page displays all configuration fields
    Given the user navigates to the Settings page
    Then the page should display fields: JIRA_HOST, AI_PROVIDER_URL, DB_PATH, JWT_SECRET, ENCRYPTION_KEY, PORT
    And the DB_PATH field should be readonly with "ENV ONLY" badge
    And the PORT field should be readonly with "ENV ONLY" badge
    And the JWT_SECRET field should be a password input (masked)
    And the ENCRYPTION_KEY field should be a password input (masked)

  @req-8.2 @req-16.1
  Scenario: Sensitive fields are masked in the response
    Given the user navigates to the Settings page
    Then the JWT_SECRET field should display only the last 4 characters
    And the ENCRYPTION_KEY field should display only the last 4 characters

  @req-8.1
  Scenario: Administrator can save settings
    Given the user navigates to the Settings page
    And the user updates JIRA_HOST to "https://newdomain.atlassian.net"
    When the user clicks "SAVE SETTINGS"
    Then a progress bar should animate
    And a success message "Settings saved successfully" should appear
    And the Backend_Server should persist the settings in the database

  @req-8.1
  Scenario: Readonly fields cannot be edited
    Given the user navigates to the Settings page
    Then the DB_PATH input should have the disabled attribute
    And the PORT input should have the disabled attribute

  @req-8.1
  Scenario: Settings validation rejects invalid URL
    Given the user navigates to the Settings page
    And the user enters "not-a-url" in the JIRA_HOST field
    When the user clicks "SAVE SETTINGS"
    Then the Backend_Server should return a 400 error
    And an error message should be displayed

  @req-11.1
  Scenario: Non-Administrator cannot access Settings page
    Given the user is authenticated with role "Neural_Architect"
    When the user navigates to "#settings"
    Then the page should display "Access Denied" message
    And the settings form should not be visible

  @req-11.1
  Scenario: Non-Administrator does not see App Settings in dropdown
    Given the user is authenticated with role "Reader"
    When the user clicks the avatar on the navigation bar
    Then the dropdown should not contain "App Settings" link

  @req-8.3
  Scenario: Settings from DB override environment variable defaults
    Given the database contains setting JIRA_HOST = "https://db-domain.atlassian.net"
    And the environment variable JIRA_HOST = "https://env-domain.atlassian.net"
    When the Backend_Server loads configuration
    Then the effective JIRA_HOST should be "https://db-domain.atlassian.net"
