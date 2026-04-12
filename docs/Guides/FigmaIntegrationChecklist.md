# Figma Integration Checklist

This checklist is for developers and designers integrating Figma designs into the Jira Assistant Compose Multiplatform app.

## 1. Prepare the Design Tokens
- [ ] Export color palette from Figma.
- [ ] Map Figma colors to Compose `Color` constants.
- [ ] Create a dedicated theme folder, for example:
  - `composeApp/src/commonMain/kotlin/com/assistant/ui/theme/Color.kt`
  - `composeApp/src/commonMain/kotlin/com/assistant/ui/theme/Type.kt`
  - `composeApp/src/commonMain/kotlin/com/assistant/ui/theme/Theme.kt`
- [ ] Avoid hardcoded `Color(0xFF...)` values in screens; use named theme colors.
- [ ] If Figma uses spacing scales, convert them to Compose `Dp` values consistently.

## 2. Establish the Component Pattern
- [ ] Identify reusable UI components from Figma (cards, lists, buttons, headers).
- [ ] Add composables under `composeApp/src/commonMain/kotlin/com/assistant/ui/components/`.
- [ ] Keep screens in `composeApp/src/commonMain/kotlin/com/assistant/ui/screens/`.
- [ ] Use `MaterialTheme` for typography, shapes, and colors.
- [ ] Prefer `Card`, `Surface`, `ListItem`, `Row`, `Column`, `Box`, and `Canvas` for layout.

## 3. Sync with App Shell and Routing
- [ ] Keep `App.kt` as the top-level state router.
- [ ] Add or update `AppViewState` cases for new screens.
- [ ] Ensure new composables can be rendered from the existing `AnimatedContent`/`when` logic.
- [ ] Keep side effects and loading logic inside `LaunchedEffect` or view-model-like wrappers.

## 4. Use the Existing Build and UI Stack
- [ ] Confirm `composeApp/build.gradle.kts` includes `compose.material3` and `compose.foundation`.
- [ ] Do not introduce React/Vue-specific libraries; keep the app in Kotlin Compose.
- [ ] If new resources are needed, use Compose resources with `publicResClass = true`.

## 5. Handle Assets and Icons
- [ ] For standard icons, use `Icons.Default.*` from Compose Material icons.
- [ ] For custom Figma icons, import them as vector assets or SVG resources.
- [ ] Keep image assets local to `composeApp/src/wasmJsMain/resources` or resource-aware Compose files.
- [ ] Avoid relying on external CDN configuration unless the project adds it explicitly.

## 6. Document the Integration
- [ ] Update `docs/Architect/TechnicalDesign.md` with any new theme or component architecture.
- [ ] Add notes to the new Figma integration checklist if the visual system changes.
- [ ] Keep the prototype HTML in `composeApp/src/main/resources/jira-dashboard.html` as a reference only.

## 7. Test the UI after integration
- [ ] Run the Compose Wasm app and validate new screens visually.
- [ ] Verify the app still compiles with the existing `shared` module.
- [ ] Ensure the UI flows remain consistent with the current `App.kt` screen transitions.
- [ ] If needed, add E2E scenarios in `e2e-tests/src/test/resources/features/` for major flows.

## 8. Mapping Figma → Compose
- [ ] Translate Figma colors into Compose theme tokens.
  - Figma color swatches -> `Color(...)` constants in `Color.kt`.
  - Use `darkColorScheme(...)` or `lightColorScheme(...)` in `Theme.kt`.
- [ ] Convert Figma typography styles into Compose text styles.
  - Figma font family/weight/size -> `Typography` in `Type.kt`.
  - Apply with `MaterialTheme.typography.titleLarge`, `bodyMedium`, `labelSmall`, etc.
- [ ] Map Figma spacing to Compose `Dp` values.
  - Figma spacing scales (4/8/12/16/24/32) -> `8.dp`, `16.dp`, etc.
  - Use reusable spacing constants if the design uses a consistent scale.
- [ ] Convert Figma corner radii to Compose shapes.
  - Example: `RoundedCornerShape(12.dp)` or `MaterialTheme.shapes.medium`.
- [ ] Map Figma layout patterns to Compose primitives:
  - horizontal alignment -> `Row` with `Arrangement.spacedBy(...)`
  - vertical stacking -> `Column` with `Spacer(modifier = Modifier.height(...))`
  - overlay/stack -> `Box`
  - grid cards -> `LazyVerticalGrid` or nested `Row`/`Column`
- [ ] Implement Figma cards and panels using `Card` or `Surface`.
  - Keep background and elevation consistent with the Figma design.
- [ ] For icons and buttons:
  - Standard icon placements -> `IconButton`, `Icon`, `Button`, `TextButton`.
  - If Figma uses custom iconography, add assets as vector resources and wrap them with composables.
- [ ] Use Compose canvas only for custom graph or illustration patterns.
  - Simple UI should be built from standard Compose layout components, not raw `Canvas`.
- [ ] Keep interactive states aligned with Figma.
  - hover/focus/press states should map to Compose `ButtonDefaults` and theming.
  - Use `CardDefaults.cardColors(...)` for card background colors, and `ButtonDefaults.buttonColors(...)` for buttons.
