# Project Structure — JIRAAssitantNewSollution

**Last Updated:** 2026-04-30T10:44:26.881Z
**Project Type:** gradle-kotlin

## Modules

| Module | Purpose | Language | Framework | Key Dependencies | Source Files |
|--------|---------|----------|-----------|-----------------|-------------|
| shared | API layer | javascript | — | — | 270 |
| server | API layer | javascript | — | — | 747 |
| server-core | Business logic | javascript | — | server | 60 |
| server-dashboard | Testing | javascript | — | server | 7 |
| server-analysis | Business logic | javascript | — | server | 130 |
| server-docgen | Business logic | javascript | — | server | 151 |
| server-agent | API layer | javascript | — | server | 233 |
| server-chat | Business logic | javascript | — | server | 70 |
| server-mcp | Business logic | javascript | — | server | 64 |
| server-knowledge-graph | Testing | javascript | — | server | 4 |
| server-user-mgmt | Testing | javascript | — | server | 5 |
| server-testing-support | Configuration | javascript | — | server | 2 |
| frontend | API layer | javascript | — | — | 194 |
| e2e-tests | API layer | javascript | — | — | 56 |

## Inter-Module Dependencies

| Module | Depends On |
|--------|-----------|
| shared | — |
| server | — |
| server-core | server |
| server-dashboard | server |
| server-analysis | server |
| server-docgen | server |
| server-agent | server |
| server-chat | server |
| server-mcp | server |
| server-knowledge-graph | server |
| server-user-mgmt | server |
| server-testing-support | server |
| frontend | — |
| e2e-tests | — |
