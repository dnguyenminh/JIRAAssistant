# Bugfix Requirements Document

## Introduction

Cửa sổ cấu hình Jira Cloud Services không nhất quán với các AI provider config modals (Ollama, Gemini, LM Studio, Gemini CLI) về cả giao diện lẫn chức năng. Jira modal sử dụng 1 nút "SAVE & TEST" gộp chung thay vì 2 nút riêng biệt "TEST CONNECTION" + "SAVE", và các input fields Email/API Token dùng nền trắng (light theme) thay vì nền tối theo Obsidian Kinetic design system. Điều này vi phạm tính nhất quán UI và dark theme của ứng dụng.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN user opens the Jira Cloud Services config modal THEN the system displays a single combined "SAVE & TEST" button that saves configuration and tests connection in one action

1.2 WHEN user opens the Jira Cloud Services config modal THEN the system displays Email and API Token input fields with white/light background, violating the Obsidian Kinetic dark theme

1.3 WHEN user opens the Jira Cloud Services config modal THEN the system allows saving configuration without first verifying the connection is successful (no SAVE disable-until-test-passes pattern)

1.4 WHEN user opens the Jira Cloud Services config modal THEN the input field styling is inconsistent within the same modal — Jira Domain URL uses dark background (via `field-input` class) but Email and API Token inputs also have `integ-config-input` class yet render with light background due to browser native styling overriding the dark theme

### Expected Behavior (Correct)

2.1 WHEN user opens the Jira Cloud Services config modal THEN the system SHALL display two separate buttons: "TEST CONNECTION" and "SAVE", matching the AI provider modal layout

2.2 WHEN user opens the Jira Cloud Services config modal THEN the system SHALL display all input fields (Jira Domain URL, Email, API Token) with dark background matching the Obsidian Kinetic design system, consistent with AI provider modals

2.3 WHEN user opens the Jira Cloud Services config modal THEN the SAVE button SHALL be disabled (opacity 0.4, pointer-events none) until TEST CONNECTION succeeds, matching the AI provider modal pattern (per requirements 10-13)

2.4 WHEN user clicks "TEST CONNECTION" in the Jira modal THEN the system SHALL test the connection using form values (domain, email, API token) without saving to database, and enable the SAVE button only on success

2.5 WHEN user clicks "SAVE" in the Jira modal (after successful test) THEN the system SHALL save the Jira configuration to database and update the provider card status

### Unchanged Behavior (Regression Prevention)

3.1 WHEN user opens any AI provider config modal (Ollama, Gemini, LM Studio, Gemini CLI) THEN the system SHALL CONTINUE TO display two separate buttons "TEST CONNECTION" and "SAVE" with SAVE disabled until test succeeds

3.2 WHEN user submits valid Jira credentials (domain, email, API token) THEN the system SHALL CONTINUE TO save configuration to database with AES-256-GCM encryption for sensitive fields

3.3 WHEN user tests Jira connection successfully THEN the system SHALL CONTINUE TO update the Jira provider card status dot to ACTIVE with green glow

3.4 WHEN user tests Jira connection and it fails THEN the system SHALL CONTINUE TO display error message and update status dot to OFFLINE

3.5 WHEN user without Administrator role opens the Jira config modal THEN the system SHALL CONTINUE TO display fields as read-only with actions disabled

3.6 WHEN user clicks the close button or clicks outside the Jira modal THEN the system SHALL CONTINUE TO close the modal and reset progress/status indicators


---

## Bug Condition Derivation

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type ConfigModalInteraction
  OUTPUT: boolean
  
  // Returns true when the modal being opened is the Jira Cloud Services modal
  RETURN X.providerType = "JIRA"
END FUNCTION
```

### Property Specification — Fix Checking

```pascal
// Property: Fix Checking — Jira Modal Consistency
FOR ALL X WHERE isBugCondition(X) DO
  modal ← openConfigModal(X)
  ASSERT modal.hasButton("TEST CONNECTION")
    AND modal.hasButton("SAVE")
    AND NOT modal.hasButton("SAVE & TEST")
    AND modal.saveButton.isDisabled = true
    AND modal.inputFields.ALL(field => field.background = darkThemeBackground)
END FOR
```

### Preservation Goal

```pascal
// Property: Preservation Checking — AI Provider Modals Unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
  // AI provider modals (Ollama, Gemini, LM Studio, Gemini CLI) 
  // continue to behave identically before and after the fix
END FOR
```
