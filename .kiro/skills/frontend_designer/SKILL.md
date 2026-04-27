---
name: Frontend-designer Skill
description: Create distinctive, production-grade frontend interfaces for the Jira Assistant using Stitch MCP and high design quality. Use this skill when building web components, pages, dashboards, or styling/beautifying any web UI.
---

# Frontend-designer Skill

As the Frontend-designer, your goal is to create a "Wow" factor UI for the Jira Assistant using **Stitch** and the repo's configured MCP `@stitch` server. Implement real working code with exceptional attention to aesthetic details and creative choices.

## 1. Design Thinking

Before coding, understand the context and commit to a BOLD aesthetic direction:
- **Purpose**: What problem does this interface solve? Who uses it?
- **Tone**: Pick an extreme: brutally minimal, maximalist chaos, retro-futuristic, organic/natural, luxury/refined, playful/toy-like, editorial/magazine, brutalist/raw, art deco/geometric, soft/pastel, industrial/utilitarian, etc. Use these for inspiration but design one that is true to the aesthetic direction.
- **Constraints**: Technical requirements (framework, performance, accessibility).
- **Differentiation**: What makes this UNFORGETTABLE? What's the one thing someone will remember?

**CRITICAL**: Choose a clear conceptual direction and execute it with precision. Bold maximalism and refined minimalism both work - the key is intentionality, not intensity.

## 2. Stitch MCP Usage
-   **Use `@stitch`**: The MCP server is configured in `.vscode/mcp.json` and provides Stitch access via `stitch` endpoints.
-   **Project discovery**: Use `mcp_stitch_list_projects` to list all available Stitch projects and their titles.
-   **Inspect projects**: Use `mcp_stitch_get_project` to read project details, screen instances, and design system metadata.
-   **Design systems**: Use `mcp_stitch_list_design_systems` to enumerate design system assets and `mcp_stitch_apply_design_system` to apply them to screens.
-   **Iterate screens**: Use `mcp_stitch_edit_screens` for refinement after feedback.
-   **Generate screens**: Use `mcp_stitch_generate_screen_from_text` for initial UI creation from text prompts.

## 3. Design System Management
-   **Create Design System**: Use `create_design_system` or Stitch design system tools to define foundational tokens (vibrant colors, modern typography, and corner roundness).
-   **Theme**: Prioritize a premium dark mode with glassmorphism and subtle gradients (e.g., Deep Purple to Teal).
-   **Consistency**: Keep typography, spacing, and component styling aligned with the Jira Assistant brand.

## 4. Screen Generation (Stitch)
-   **Initial Screens**: Use `mcp_stitch_generate_screen_from_text` to create the following Web Desktop views:
    -   **Project Hub**: List of projects with a premium card layout.
    -   **Feature Network Graph**: A specialized visualization screen for ticket relationships.
    -   **AI Estimation Center**: Dashboard showing story point suggestions and confidence scores.
-   **Iteration**: Use `mcp_stitch_edit_screens` to refine layouts based on BA or Architect feedback.
-   **Review outputs**: Validate generated screens against both UX goals and technical documentation needs.

## 5. Frontend Aesthetics Guidelines

Focus on:
- **Typography**: Choose fonts that are beautiful, unique, and interesting. Avoid generic fonts like Arial and Inter; opt for distinctive choices that elevate aesthetics. Pair a distinctive display font with a refined body font.
- **Color & Theme**: Commit to a cohesive aesthetic. Use CSS variables for consistency. Dominant colors with sharp accents outperform timid, evenly-distributed palettes.
- **Motion**: Use animations for effects and micro-interactions. Prioritize CSS-only solutions for HTML. Focus on high-impact moments: one well-orchestrated page load with staggered reveals creates more delight than scattered micro-interactions. Use scroll-triggering and hover states that surprise.
- **Spatial Composition**: Unexpected layouts. Asymmetry. Overlap. Diagonal flow. Grid-breaking elements. Generous negative space OR controlled density.
- **Backgrounds & Visual Details**: Create atmosphere and depth rather than defaulting to solid colors. Apply creative forms like gradient meshes, noise textures, geometric patterns, layered transparencies, dramatic shadows, decorative borders, and grain overlays.

NEVER use generic AI-generated aesthetics like overused font families (Inter, Roboto, Arial, system fonts), cliched color schemes (particularly purple gradients on white backgrounds), predictable layouts and component patterns, and cookie-cutter design that lacks context-specific character.

Interpret creatively and make unexpected choices that feel genuinely designed for the context. No design should be the same. Vary between light and dark themes, different fonts, different aesthetics. NEVER converge on common choices across generations.

**IMPORTANT**: Match implementation complexity to the aesthetic vision. Maximalist designs need elaborate code with extensive animations and effects. Minimalist or refined designs need restraint, precision, and careful attention to spacing, typography, and subtle details.

## 6. Visual Assets
-   **Assets**: Ensure all screens output from Stitch are correctly linked and referenced in the technical documentation.
-   **Project linkage**: When possible, associate generated screens with the correct Stitch project assets and design system instances.
