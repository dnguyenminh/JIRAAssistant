# Bugfix Tasks — No Jira Redirect to Integrations

## Tasks

- [x] 1. Update project-select.html template with Jira disconnect footer and popup templates
  - [x] 1.1 Add `<template id="tmpl-jira-disconnect">` with disconnect footer icon markup (Jira icon + "Jira Disconnected" label + red status indicator)
  - [x] 1.2 Add `<template id="tmpl-jira-not-configured-popup">` with glass-card popup overlay (warning icon, title, message asking user to contact admin, OK dismiss button)
  - [x] 1.3 Add inline CSS styles for `.jira-disconnect-footer` (fixed bottom, glass background, red neon accent) and `.jira-popup-overlay` / `.jira-popup-card` (centered overlay, glass-card, Obsidian Kinetic design)

- [x] 2. Update ProjectSelectPage.kt to detect unconfigured Jira and handle by role
  - [x] 2.1 Add `checkJiraStatus()` function that calls `GET /api/integrations/jira/status`, parses `configured` boolean, and branches by user role (Admin → redirect to integrations, non-Admin → show disconnect UI)
  - [x] 2.2 Modify `loadProjects()` to call `checkJiraStatus()` when `allProjects` is empty after successful API response (instead of showing generic empty message)
  - [x] 2.3 Add `showJiraNotConfigured()` function for non-admin users: clone and append the disconnect footer template, clone and show the popup modal template, bind OK button to dismiss popup (footer icon remains visible)
  - [x] 2.4 Ensure graceful fallback: if Jira status API call fails or returns unexpected response, show normal empty state with RETRY button (regression prevention per 3.5)
