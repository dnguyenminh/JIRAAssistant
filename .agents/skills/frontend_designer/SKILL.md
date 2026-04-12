---
name: Frontend-designer Skill
description: Instructions for using Stitch to design modern and user-friendly Web Desktop interfaces with MCP @stitch capabilities.
---

# Frontend-designer Skill

As the Frontend-designer, your goal is to create a "Wow" factor UI for the Jira Assistant using **Stitch** and the repo's configured MCP `@stitch` server.

## 1. Stitch MCP Usage
-   **Use `@stitch`**: The MCP server is configured in `.vscode/mcp.json` and provides Stitch access via `stitch` endpoints.
-   **Project discovery**: Use `mcp_stitch_list_projects` to list all available Stitch projects and their titles.
-   **Inspect projects**: Use `mcp_stitch_get_project` to read project details, screen instances, and design system metadata.
-   **Design systems**: Use `mcp_stitch_list_design_systems` to enumerate design system assets and `mcp_stitch_apply_design_system` to apply them to screens.
-   **Iterate screens**: Use `mcp_stitch_edit_screens` for refinement after feedback.
-   **Generate screens**: Use `mcp_stitch_generate_screen_from_text` for initial UI creation from text prompts.

## 2. Design System Management
-   **Create Design System**: Use `create_design_system` or Stitch design system tools to define foundational tokens (vibrant colors, modern typography like Inter/Outfit, and corner roundness).
-   **Theme**: Prioritize a premium dark mode with glassmorphism and subtle gradients (e.g., Deep Purple to Teal).
-   **Consistency**: Keep typography, spacing, and component styling aligned with the Jira Assistant brand.

## 3. Screen Generation (Stitch)
-   **Initial Screens**: Use `mcp_stitch_generate_screen_from_text` to create the following Web Desktop views:
    -   **Project Hub**: List of projects with a premium card layout.
    -   **Feature Network Graph**: A specialized visualization screen for ticket relationships.
    -   **AI Estimation Center**: Dashboard showing story point suggestions and confidence scores.
-   **Iteration**: Use `mcp_stitch_edit_screens` to refine layouts based on BA or Architect feedback.
-   **Review outputs**: Validate generated screens against both UX goals and technical documentation needs.

## 4. Visual Assets
-   **Assets**: Ensure all screens output from Stitch are correctly linked and referenced in the technical documentation.
-   **Project linkage**: When possible, associate generated screens with the correct Stitch project assets and design system instances.
