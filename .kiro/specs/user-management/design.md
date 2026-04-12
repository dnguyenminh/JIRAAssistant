# User Management — Design

> **Note:** The User Management screen (MH7) does not have a dedicated section in the main frontend-screens design document. Its design follows the standard Kotlin/JS + HTML Templates architecture described in the project's frontend architecture guide.

## Architecture Reference

User Management follows the same VIEW/CONTROLLER pattern as all other screens:

- **VIEW**: `resources/templates/user-management.html` — HTML template with user list, role dropdowns, permission toggles, and audit log console
- **CONTROLLER**: `pages/UserManagementPage.kt` — Kotlin/JS page controller handling DOM manipulation, API calls, and event binding
- **CSS**: Shared component styles from `components.css` (glass-card, glass-input, status-dot, neural-console)

## Key UI Components

- User list with avatar (48px), name, email, role dropdown selector
- Permission panel with 4 toggles: Trigger AI Scan, Knowledge Base Write, Update Integrations, Export Neural Data
- IAM SYNC ticker animation on permission changes
- Neural Console at bottom showing audit log entries (timestamp, tag, description)
- Access via User Profile Dropdown (Account Settings), not sidebar navigation

## API Endpoints Used

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/users` | GET | List all users |
| `/api/users/{id}/role` | PUT | Change user role |
| `/api/users/{id}/permissions` | PUT | Toggle permission |

## RBAC

- Only Administrator role can access this page (403 for others)
- Role changes apply immediately
- All changes logged to audit log
