# BRD Diagram & Sections Fix — Bugfix Design

## Overview

This bugfix addresses two BRD document quality issues: (1) draw.io diagrams fail to render when the external CDN is unreachable, and (2) the AI prompt does not strongly enforce substantive content in all 7 BRD sections, leading to "Insufficient data" placeholders. The fix bundles the draw.io viewer locally and strengthens prompt instructions in `AgenticPromptSections.kt` and `PhasePromptBuilder.kt`.

## Glossary

- **Bug_Condition (C)**: Two conditions — CDN script load failure causing diagram fallback, and weak prompt instructions allowing empty/placeholder BRD sections
- **Property (P)**: Diagrams render from local bundle; all 7 BRD sections contain mandatory substantive content instructions in the prompt
- **Preservation**: Existing diagram rendering pipeline (`GraphViewer.createViewerForElement`), fallback behavior for invalid XML, `BrdResponseParser` parsing logic, and `DocumentQualityChecker` detection remain unchanged
- **DrawioDiagramRenderer**: Object in `frontend/.../DrawioDiagramRenderer.kt` that loads the draw.io viewer script and renders `<mxGraphModel>` XML into SVG diagrams
- **VIEWER_CDN**: Constant in `DrawioDiagramRenderer` holding the script URL — currently `https://viewer.diagrams.net/js/viewer-static.min.js`
- **appendBrdSections()**: Extension function on `StringBuilder` in `AgenticPromptSections.kt` that appends BRD section headings and content instructions to the AI prompt
- **appendPhase2Task()**: Extension function in `PhasePromptBuilder.kt` that appends the Phase 2 (BRD writing) task instructions

## Bug Details

### Bug Condition

Two independent bug conditions exist:

**Bug 1 — CDN Viewer Failure:** The draw.io viewer script is loaded from an external CDN (`https://viewer.diagrams.net/js/viewer-static.min.js`). When the CDN is unreachable (offline, firewall, CORS, downtime), `loadViewerScript()` fires the `error` event listener, `viewerLoaded` stays `false`, and all diagrams fall back to raw XML display.

**Bug 2 — Weak Section Enforcement:** The `appendBrdSections()` function only says "Each section MUST have real content (3+ lines minimum)" without explicitly prohibiting placeholder text like "Insufficient data" or "N/A". The AI sometimes produces empty or placeholder content, which `BrdResponseParser.parse()` then fills with the "⚠️ Insufficient data" marker.

**Bug 3 — Raw XML Stripped by Markdown Parser (added post-implementation):** When `BrdAssembler` injects raw `<mxGraphModel>` XML into BRD markdown (not inside code fences), `marked.js` in `MarkdownRenderer` treats the XML as HTML tags and strips them entirely. `DocumentPreviewPanel.renderDiagramsInContent()` then finds zero `<code>` blocks containing `<mxGraphModel>` → no diagrams rendered. **Fix:** `MarkdownRenderer.wrapRawXmlInCodeFences()` pre-processes markdown before `marked.js` parsing, wrapping raw `<mxGraphModel>...</mxGraphModel>` blocks in ` ```xml ` code fences so they survive as `<code>` elements.

**Bug 4 — Bare `&` and Duplicate Attributes in AI-Generated XML Break GraphViewer (added post-implementation):** AI generates XML with two types of errors: (1) unescaped `&` in `value` attributes (e.g., `value="Bulk Upload & File Processing"`) causing "xmlParseEntityRef: no name", and (2) duplicate attributes on the same element (e.g., `edge="1" ... edge="1"`) causing "Attribute edge redefined". **Fix:** `DocumentPreviewPanel.sanitizeDrawioXml()` applies two sanitizations: escapes bare `&` to `&amp;`, and removes duplicate attributes (keeps first occurrence) via `deduplicateAttrs()`. Fallback display also shows full XML instead of truncated 500 chars.

**Formal Specification:**

```
FUNCTION isBugCondition_DiagramViewer(input)
  INPUT: input of type ScriptLoadEvent
  OUTPUT: boolean

  RETURN input.scriptSrc = "https://viewer.diagrams.net/js/viewer-static.min.js"
         AND input.loadResult = FAILURE
         AND viewerLoaded = false
END FUNCTION

FUNCTION isBugCondition_InsufficientData(input)
  INPUT: input of type PromptOutput
  OUTPUT: boolean

  RETURN input.promptText DOES NOT CONTAIN "NEVER write 'Insufficient data'"
         AND input.promptText DOES NOT CONTAIN explicit prohibition of placeholder text
END FUNCTION

FUNCTION isBugCondition_RawXmlStripped(input)
  INPUT: input of type MarkdownContent
  OUTPUT: boolean

  RETURN input.markdown CONTAINS "<mxGraphModel>"
         AND input.markdown DOES NOT CONTAIN "```xml\n<mxGraphModel>"
END FUNCTION

FUNCTION isBugCondition_BareAmpersand(input)
  INPUT: input of type DrawioXml
  OUTPUT: boolean

  RETURN input.xml MATCHES "&(?!amp;|lt;|gt;|quot;|apos;|#)"
END FUNCTION
```

### Examples

- **Offline environment**: User opens BRD preview → CDN script fails → all 3 diagrams show "📊 Draw.io diagram (viewer unavailable)" with truncated XML → Expected: diagrams render from local `/js/viewer-static.min.js`
- **Firewall block**: Corporate proxy blocks `viewer.diagrams.net` → same fallback → Expected: local bundle loads successfully
- **AI skips Project Requirements**: Phase 2 AI produces empty "Project Requirements" section → `BrdResponseParser` fills with "⚠️ Insufficient data" → Expected: prompt explicitly prohibits this and requires sub-sections
- **AI writes "N/A" for Sign Off**: AI outputs "N/A" for Sign Off section → parser treats as valid content but it's useless → Expected: prompt prohibits "N/A" as section content
- **Raw XML in BRD markdown**: BrdAssembler injects `<mxGraphModel>` XML directly into markdown → `marked.js` strips it → diagrams invisible in preview → Expected: `wrapRawXmlInCodeFences()` wraps XML in code fences before parsing

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- `GraphViewer.createViewerForElement(container)` rendering pipeline works identically whether script is loaded from CDN or local path
- `initViewer()` JS function that sets up `window.__drawioRenderOne` remains unchanged
- `renderWithViewer()` logic for setting `data-drawio-xml` attribute and calling the helper remains unchanged
- When viewer loads but a specific diagram has invalid XML, the fallback display ("📊 Draw.io diagram (viewer unavailable)") still shows
- `BrdResponseParser.parse()` continues to fill genuinely missing section headings with the "Insufficient data" safety fallback
- `DocumentQualityChecker.check()` continues to detect and flag any remaining "Insufficient data" markers
- Phase 3 diagram generation prompts and templates work unchanged
- `BrdPromptBuilder.BRD_SECTIONS` list remains the single source of truth for section headings

**Scope:**
All inputs that do NOT involve CDN script loading or BRD section prompt generation should be completely unaffected. This includes:
- Mouse interactions with diagram containers
- Non-BRD document types (FSD)
- Phase 1 (data collection) and Phase 3 (diagram generation) prompts
- Diagram template merging (`DrawioTemplateEngine.merge`)

## Hypothesized Root Cause

Based on the bug analysis, the root causes are:

1. **External CDN dependency for diagram viewer**: `DrawioDiagramRenderer.VIEWER_CDN` points to `https://viewer.diagrams.net/js/viewer-static.min.js`. Any network issue (offline, firewall, CORS, CDN downtime) causes the `<script>` element's `error` event to fire, and `onFail` is called. The fix is to bundle the script locally at `/js/viewer-static.min.js` and change the constant.

2. **Insufficient prompt enforcement in `appendBrdSections()`**: The function only says "Each section MUST have real content (3+ lines minimum)" but does not:
   - Explicitly prohibit "Insufficient data", "N/A", "No data available" placeholder text
   - Require specific sub-sections for "Project Requirements" (Process Overview, Functional Requirements, Non-Functional Requirements, Data Requirements)
   - Instruct the AI to infer content from available data when specific data is lacking

3. **Phase 2 task instruction lacks reinforcement**: `appendPhase2Task()` says "Include all 7 sections with real content" but does not reinforce the prohibition of placeholder text or list the 7 section names explicitly.

## Correctness Properties

Property 1: Bug Condition — Local Viewer Bundle Loads Successfully

_For any_ diagram render request where the draw.io viewer script is needed, the fixed `DrawioDiagramRenderer` SHALL load the script from the local path `/js/viewer-static.min.js` instead of the external CDN, ensuring `ensureViewerLoaded()` succeeds without internet access.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition — Prompt Contains Mandatory Content Instructions

_For any_ call to `appendBrdSections()`, the output prompt text SHALL contain explicit prohibition of "Insufficient data", "N/A", and similar placeholder text, AND SHALL require the "Project Requirements" section to include Process Overview, Functional Requirements, Non-Functional Requirements, and Data Requirements sub-sections.

**Validates: Requirements 2.3, 2.5**

Property 3: Bug Condition — Phase 2 Prompt Contains Section Enforcement

_For any_ call to `appendPhase2Task()`, the output prompt text SHALL contain all 7 BRD section names AND a mandatory content instruction reinforcing that every section must have substantive content.

**Validates: Requirements 2.4**

Property 4: Preservation — Diagram Rendering Pipeline Unchanged

_For any_ diagram render request where the viewer script loads successfully (from any source), the fixed code SHALL produce the same rendering behavior as the original code, preserving the `GraphViewer.createViewerForElement` pipeline, fallback behavior for invalid XML, and `initViewer()` setup.

**Validates: Requirements 3.1, 3.2, 3.5**

Property 5: Preservation — BRD Parsing and Quality Checking Unchanged

_For any_ BRD document where all 7 sections contain substantive content, the fixed code SHALL produce the same parsing result via `BrdResponseParser` and the same quality check result via `DocumentQualityChecker` as the original code.

**Validates: Requirements 3.3, 3.4, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File 1**: `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/DrawioDiagramRenderer.kt`

**Change**: Update `VIEWER_CDN` constant

**Specific Changes**:
1. **Change CDN URL to local path**: Replace `"https://viewer.diagrams.net/js/viewer-static.min.js"` with `"/js/viewer-static.min.js"`
2. No other changes needed — `loadViewerScript()`, `ensureViewerLoaded()`, `initViewer()`, `renderWithViewer()` all work identically with a local path

---

**File 2**: `frontend/src/jsMain/resources/js/viewer-static.min.js`

**Change**: Add new bundled file

**Specific Changes**:
1. **Download and bundle** the draw.io viewer script (~1.5MB minified JavaScript) from the draw.io open source project (Apache 2.0 license)
2. Place at `frontend/src/jsMain/resources/js/viewer-static.min.js` so Vite serves it at `/js/viewer-static.min.js`

---

**File 3**: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/AgenticPromptSections.kt`

**Function**: `appendBrdSections()`

**Specific Changes**:
1. **Add explicit prohibition**: After the existing "Each section MUST have real content" line, add instruction: `"CRITICAL: EVERY section MUST contain real, substantive content. NEVER write 'Insufficient data', 'N/A', 'No data available', or similar placeholders."`
2. **Add Project Requirements sub-section requirements**: Add instruction specifying that "Project Requirements" must include: Process Overview, Functional Requirements, Non-Functional Requirements, Data Requirements
3. **Add inference instruction**: Add `"If you lack specific data for a section, use your analysis of the ticket to infer reasonable content. Every section must have at least 3 lines of real content."`

---

**File 4**: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/pipeline/aibackend/PhasePromptBuilder.kt`

**Function**: `appendPhase2Task()`

**Specific Changes**:
1. **Reinforce section enforcement**: Expand the Phase 2 task instruction to list all 7 section names explicitly
2. **Add mandatory content instruction**: Add reinforcement that all 7 sections must have substantive content, no placeholders allowed

---

**File 5** (added post-implementation): `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/MarkdownRenderer.kt`

**Function**: `render()` + new `wrapRawXmlInCodeFences()`

**Specific Changes**:
1. **Pre-process markdown before `marked.js`**: Call `wrapRawXmlInCodeFences(md)` before `parseMarkdown()` to wrap raw `<mxGraphModel>...</mxGraphModel>` blocks in ` ```xml ` code fences
2. **New top-level regex**: `MX_GRAPH_RAW = Regex("<mxGraphModel>[\\s\\S]*?</mxGraphModel>")` matches raw XML blocks not already inside code fences
3. **No other changes**: `loadMarkedJs()`, `configureMarked()`, `sanitizeHtml()`, `wrapRawText()` remain unchanged

---

**File 6** (added post-implementation): `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/ticket/DocumentPreviewPanel.kt`

**Functions**: `renderDrawioBlock()`, `showDiagramFallback()`, new `sanitizeDrawioXml()`

**Specific Changes**:
1. **XML sanitization**: `renderDrawioBlock()` calls `sanitizeDrawioXml(xml)` before setting `data-drawio-xml` attribute. `sanitizeDrawioXml()` applies two fixes: (a) regex `&(?!amp;|lt;|gt;|quot;|apos;|#)` escapes bare `&` to `&amp;`, (b) `deduplicateAttrs()` processes each XML tag via `XML_TAG_WITH_ATTRS` regex, keeping only the first occurrence of each attribute name
2. **Full fallback display**: `showDiagramFallback()` changed from `xml.take(500) + "..."` to full `xml` — no more truncation
3. **New top-level regexes**: `BARE_AMPERSAND` for ampersand escaping, `XML_TAG_WITH_ATTRS` for tag-level attribute deduplication

---

**File 7** (added post-implementation): `frontend/src/jsMain/resources/ticket-intelligence.css`

**Specific Changes**:
1. **`.drawio-inline-diagram`**: White background (`#ffffff`), `border-radius: 8px`, `padding: 16px`, border — ensures diagram SVG with default black strokes/arrows is visible on the dark Obsidian Kinetic theme
2. **`.drawio-inline-diagram svg`**: `max-width: 100%` for responsive scaling
3. **`.drawio-fallback`**: Dark semi-transparent background for fallback raw XML display
4. **`.drawio-fallback-label`** + **`.drawio-fallback-xml`**: Styled fallback text with max-height scroll

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed code, then verify the fixes work correctly and preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis.

**Test Plan**: Examine the current code to verify that (1) `VIEWER_CDN` points to external CDN, and (2) `appendBrdSections()` output lacks explicit placeholder prohibition. Run in offline mode to observe diagram fallback.

**Test Cases**:
1. **CDN Failure Test**: Disconnect network → open BRD preview with diagrams → observe fallback display (will fail on unfixed code)
2. **Prompt Content Test**: Call `appendBrdSections("BRD")` on a `StringBuilder` → check output does NOT contain "NEVER" + "Insufficient data" prohibition (will fail on unfixed code)
3. **Phase 2 Prompt Test**: Call `appendPhase2Task()` → check output does NOT list all 7 section names (will fail on unfixed code)
4. **AI Generation Test**: Generate BRD with minimal ticket data → observe "Insufficient data" in output (may fail on unfixed code)

**Expected Counterexamples**:
- Diagram viewer shows fallback when CDN is unreachable
- Prompt output lacks explicit prohibition of placeholder text
- Possible causes: external CDN dependency, weak prompt instructions

### Fix Checking

**Goal**: Verify that for all inputs where the bug conditions hold, the fixed functions produce the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition_DiagramViewer(input) DO
  result := ensureViewerLoaded'(input)
  ASSERT result.scriptSrc = "/js/viewer-static.min.js"
    AND result.viewerLoaded = true
END FOR

FOR ALL input WHERE isBugCondition_InsufficientData(input) DO
  result := appendBrdSections'(input)
  ASSERT result CONTAINS "NEVER write 'Insufficient data'"
    AND result CONTAINS "Process Overview"
    AND result CONTAINS "Functional Requirements"
    AND result CONTAINS "Non-Functional Requirements"
    AND result CONTAINS "Data Requirements"
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug conditions do NOT hold, the fixed functions produce the same result as the original functions.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition_DiagramViewer(input) DO
  ASSERT renderDiagram(input) = renderDiagram'(input)
END FOR

FOR ALL input WHERE NOT isBugCondition_InsufficientData(input) DO
  ASSERT parseBrd(input) = parseBrd'(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for diagram rendering with valid XML and BRD parsing with complete sections, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Diagram Rendering Preservation**: Verify that when viewer loads successfully, `renderWithViewer()` produces identical SVG output regardless of script source (local vs CDN)
2. **BRD Parsing Preservation**: Verify that `BrdResponseParser.parse()` returns identical results for BRDs with all 7 sections containing substantive content
3. **Quality Checker Preservation**: Verify that `DocumentQualityChecker.check()` returns identical results for complete BRDs
4. **Fallback Preservation**: Verify that when viewer loads but specific diagram XML is invalid, fallback display still shows correctly

### Unit Tests

- Test that `VIEWER_CDN` constant equals `/js/viewer-static.min.js`
- Test that `appendBrdSections()` output contains "NEVER" + "Insufficient data" prohibition
- Test that `appendBrdSections()` output contains all 4 Project Requirements sub-sections
- Test that `appendPhase2Task()` output contains all 7 BRD section names
- Test that `appendPhase2Task()` output contains mandatory content instruction
- Test that `BrdResponseParser.parse()` still fills genuinely missing sections with default text

### Property-Based Tests

- Generate random BRD markdown with all 7 sections present → verify `BrdResponseParser.parse()` returns all sections unchanged
- Generate random prompt builder calls → verify `appendBrdSections()` always contains prohibition text
- Generate random diagram XML → verify rendering pipeline produces same output with local vs CDN script

### Integration Tests

- Full BRD generation flow: trigger Phase 2 → verify prompt contains strengthened instructions → verify output has all 7 sections
- Full diagram rendering flow: load BRD preview → verify diagrams render from local bundle → verify fallback still works for invalid XML
- Quality checker integration: generate BRD → run quality check → verify "Insufficient data" markers are flagged
