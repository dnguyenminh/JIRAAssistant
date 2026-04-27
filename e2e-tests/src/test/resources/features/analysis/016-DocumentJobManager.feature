@ui
Feature: Document Job Manager — Background Job Management & Document Generation UI

  As a BA/PM
  I want to generate BRD/FSD/Slides documents via background jobs with dependency chain,
  review/approve workflow, and version history
  So that I can manage document generation efficiently without waiting on the page

  # Validates: Requirements 1.1–9.7 from document-job-manager spec

  Background:
    Given the user is authenticated with role "Neural_Architect"
    And the user has selected project "PROJ"
    And the user navigates to the Ticket Intelligence page

  # ── Requirement 1: Dependency Chain ────────────────────────

  @req-1.6
  Scenario: Document Generation section shows Generate All button
    Given the user has selected an analyzed ticket
    Then the DOCUMENT GENERATION section should be visible
    And the "GENERATE ALL" button should be displayed
    And the "GENERATE BRD" button should be displayed
    And the "GENERATE FSD" button should be displayed
    And the "GENERATE SLIDES" button should be displayed

  @req-9.3
  Scenario: FSD and Slides buttons show dependency tooltip when no BRD exists
    Given the user has selected an analyzed ticket with no BRD document
    Then the "GENERATE FSD" button should have tooltip "Cần sinh BRD trước"
    And the "GENERATE SLIDES" button should have tooltip "Cần sinh BRD trước"

  @req-1.6
  Scenario: Generate All button is disabled when no deep analysis
    Given the user has selected a ticket that is not analyzed
    Then the DOCUMENT GENERATION section should be hidden

  # ── Requirement 4: Global Job Indicator ────────────────────

  @req-4.1 @req-4.2
  Scenario: Global Job Indicator badge hidden when no active jobs
    Then the global job indicator badge should not be visible on the navbar

  @req-4.1
  Scenario: Global Job Indicator badge shows active job count
    Given there are active generation jobs
    Then the global job indicator badge should show the active job count

  # ── Requirement 5: Generation Lock ─────────────────────────

  @req-5.2
  Scenario: Generate button disabled when active job exists for same document type
    Given the user has selected an analyzed ticket with an active BRD job
    Then the "GENERATE BRD" button should be disabled
    And the "GENERATE BRD" button should show "Đang sinh..."

  @req-5.3
  Scenario: Generate button re-enabled after job completes
    Given the user has selected an analyzed ticket with a completed BRD job
    Then the "GENERATE BRD" button should be enabled

  # ── Requirement 6: Review/Approve Workflow ─────────────────

  @req-6.2 @req-9.5
  Scenario: DRAFT badge displayed for generated document
    Given the user has selected an analyzed ticket with a DRAFT BRD document
    Then a "DRAFT" badge should be displayed next to the BRD button
    And the badge should have yellow styling

  @req-6.2 @req-9.6
  Scenario: APPROVED badge displayed with version number
    Given the user has selected an analyzed ticket with an APPROVED BRD document
    Then an "APPROVED" badge should be displayed next to the BRD button
    And the badge should have green styling

  @req-6.2 @req-9.7
  Scenario: REJECTED badge displayed with reason
    Given the user has selected an analyzed ticket with a REJECTED BRD document
    Then a "REJECTED" badge should be displayed next to the BRD button
    And the badge should have red styling

  # ── Requirement 6.8: RBAC ──────────────────────────────────

  @req-6.8
  Scenario: Reader role cannot see approve/reject buttons
    Given the user is authenticated with role "Reader"
    And the user navigates to the Ticket Intelligence page
    And the user has selected an analyzed ticket
    Then the DOCUMENT GENERATION section buttons should be disabled

  # ── Requirement 9: Frontend UI Updates ─────────────────────

  @req-9.1
  Scenario: Page loads active jobs and documents on ticket selection
    Given the user has selected an analyzed ticket
    Then the Frontend should call the active-jobs endpoint
    And the Frontend should call the documents endpoint

  @req-9.4
  Scenario: Inline progress bar shown for active jobs
    Given the user has selected an analyzed ticket with an active BRD job
    Then an inline progress bar should be visible for the BRD job
    And the progress bar should show the phase label and progress percent
