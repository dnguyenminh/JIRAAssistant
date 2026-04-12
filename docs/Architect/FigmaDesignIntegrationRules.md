# Figma Design Integration Rules

This document summarizes the Jira Assistant codebase from a Figma integration perspective. It is based on the repository structure, the `docs` folder, and the source code in `composeApp` and `shared`.

---

## 1. Design System Structure

### Token Definitions
- There is no dedicated design token file or JSON/YAML token system in the current repo.
- Colors and typography are defined inline in Compose UI code, typically through `MaterialTheme` and hardcoded `Color` values.
- Example token-like values in `composeApp/src/commonMain/kotlin/com/assistant/ui/App.kt`:

```kotlin
MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Color(0xFFBB86FC),
        secondary = Color(0xFF03DAC6),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E)
    )
)
```

- The `jira-dashboard.html` prototype demonstrates a separate CSS token pattern using CSS variables:

```css
:root{--bg:#f6f7fb;--card:#fff;--muted:#6b7280;--accent:#0b69ff}
```

- There is no token transformation pipeline or tool like Style Dictionary; tokens are currently implicit/hardcoded.

### Component Library
- The UI components live in `composeApp/src/commonMain/kotlin/com/assistant/ui/`.
- Primary components:
  - `App.kt` — app shell, state routing, screens
  - `EstimationView.kt` — estimation summary card
  - `TicketGraph.kt` — graph rendering using Compose `Canvas`
- Components follow Jetpack Compose declarative UI patterns.
- There is no separate Storybook, component documentation, or dedicated UI library in this repository.

### Frameworks & Libraries
- UI framework: **Compose Multiplatform (HTML/Wasm)**.
- Styling/UI framework: **Compose Material 3**.
- Build system: **Gradle Kotlin DSL** with Kotlin Multiplatform and Compose plugins.
- Relevant build configuration in `composeApp/build.gradle.kts`:

```kotlin
kotlin {
    wasmJs {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
```

- There is no React/Vue/Angular present; the app is built with Kotlin Compose.

### Asset Management
- Static assets are minimal and located in Compose resources:
  - `composeApp/src/wasmJsMain/resources/index.html`
  - `composeApp/src/main/resources/jira-dashboard.html`
- `composeApp/build.gradle.kts` enables Compose resource generation with `publicResClass`.
- No image/video asset directories were found in the repository.
- No explicit asset optimization or CDN configuration exists in the codebase.
- The `jira-dashboard.html` file is a prototype page with inline CSS and JavaScript; it is not tied to the Compose UI runtime.

### Icon System
- Icons are imported from Compose Material icons:
  - `androidx.compose.material.icons.Icons`
  - `androidx.compose.material.icons.filled.ArrowForward`
- There is no custom icon font or SVG icon library in the repo.
- Example usage in `App.kt`:

```kotlin
Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.LightGray)
```

- The naming convention is the standard Compose Material icon names; there is no separate naming system.

### Styling Approach
- The app uses a **Compose styling approach**.
- Global theme values are applied through `MaterialTheme(...)` in `App.kt`.
- Component styling is mostly inline via modifiers and Material theming.
- Example patterns:
  - `Modifier.fillMaxSize().padding(32.dp)`
  - `colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))`
  - `MaterialTheme.typography.displayMedium`
- Responsive behavior is limited to Compose layout primitives; there is no explicit breakpoint system or media-query abstraction in Kotlin UI.
- The separate HTML prototype uses CSS grid and media query for `max-width: 640px`.

### Project Structure
- The repository is organized into three main modules:
  - `shared` — core business logic, AI integration, Jira REST client, domain models
  - `composeApp` — UI layer using Compose Multiplatform
  - `e2e-tests` — end-to-end test harness

- Within `composeApp`, UI is organized under `src/commonMain/kotlin/com/assistant/ui/`.
- The `shared` module contains:
  - `ai/` — AI agent abstractions and implementations
  - `domain/` — estimation, mapping, models
  - `jira/` — Jira client and REST wrapper

- `docs/` contains mostly business and architecture documentation:
  - `docs/Architect/TechnicalDesign.md`
  - `docs/BA/BRD.md`
  - `docs/BA/SRS.md`
  - `docs/Guides/UserGuide_StepByStep_VN.md`
  - `docs/QA/TestScenarios.md`

- Only `docs/Architect/TechnicalDesign.md` contains explicit UI architecture details.

---

## 2. Practical Rules for Figma Integration

### Rule 1: Map Figma tokens to Compose theme constants
- Create a dedicated design token module before adding Figma integration.
- Convert Figma colors into Compose `Color(...)` values and `darkColorScheme(...)`.
- Example target structure:

```kotlin
object AppColors {
    val Primary = Color(0xFFBB86FC)
    val Secondary = Color(0xFF03DAC6)
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
}
```

### Rule 2: Keep component definitions in `composeApp/src/commonMain/kotlin/com/assistant/ui`
- Add Figma-driven components as new composables in this folder.
- Prefer small reusable composables over large screen functions.
- Maintain a single `App.kt` shell that delegates to screens and composables.

### Rule 3: Use Compose Material 3 as the central styling system
- Keep all theme bindings in one place: `App.kt` or a dedicated theme file.
- Avoid scattering raw `Color(0xFF...)` values across components; use theme tokens.
- Use `MaterialTheme.typography` and `MaterialTheme.shapes` for text and shape consistency.

### Rule 4: Treat `jira-dashboard.html` as a prototype reference only
- The HTML file is not part of the Compose Web/Wasm runtime.
- Use it as a visual guideline, but implement actual app screens in Compose.
- If Figma designs are captured, map them to Compose layouts (`Column`, `Row`, `Box`, `Canvas`) rather than HTML/CSS directly.

### Rule 5: Keep assets and icons lightweight
- Use Compose Material icons for standard navigational glyphs.
- If Figma includes custom icons, add them as vector assets or SVGs in Compose resources and import via the Compose resource system.
- There is no current CDN or asset optimization pipeline, so keep assets small and local.

### Rule 6: Align integration with existing docs context
- Update `docs/Architect/TechnicalDesign.md` when adding major new UI components or design tokens.
- Do not assume `docs/BA/*` or `docs/QA/*` contain implementation details; they are business/QA artifacts.

---

## 3. Summary of Design System Findings

- The codebase currently has a lightweight UI system, not a full design system.
- Composition is done in Kotlin Compose with inline theming, rather than a tokenized design system.
- The current project is best described as a Compose prototype with a minimal static prototype page.
- A robust Figma integration should introduce:
  - a dedicated token file/module,
  - composable UI primitives for repeated patterns,
  - a theme wrapper for global styles,
  - and a clear separation between prototype HTML and Compose implementation.

---

## 4. Recommended File Locations for Figma Integration

- `composeApp/src/commonMain/kotlin/com/assistant/ui/theme/Theme.kt`
- `composeApp/src/commonMain/kotlin/com/assistant/ui/theme/Color.kt`
- `composeApp/src/commonMain/kotlin/com/assistant/ui/theme/Type.kt`
- `composeApp/src/commonMain/kotlin/com/assistant/ui/components/`
- `composeApp/src/commonMain/kotlin/com/assistant/ui/screens/`

These files will help keep Figma-derived styling and structure aligned with the existing Compose Multiplatform architecture.
