# Implementation Plan

- [x] 1. Write bug condition exploration tests
  - **Property 1: Bug Condition** — CDN Dependency & Weak Prompt Enforcement
  - **CRITICAL**: These tests MUST FAIL on unfixed code — failure confirms the bugs exist
  - **DO NOT attempt to fix the tests or the code when they fail**
  - **NOTE**: These tests encode the expected behavior — they will validate the fix when they pass after implementation
  - **GOAL**: Surface counterexamples that demonstrate both bugs exist
  - **Bug 1 — CDN Dependency**: Verify `DrawioDiagramRenderer.VIEWER_CDN` currently points to external CDN `https://viewer.diagrams.net/js/viewer-static.min.js` instead of local `/js/viewer-static.min.js`
  - **Bug 2 — Weak Prompt Enforcement**: Call `appendBrdSections("BRD")` on a `StringBuilder` and verify the output does NOT contain explicit prohibition of "Insufficient data" or "N/A" placeholder text
  - **Bug 2b — Phase 2 Task**: Call `appendPhase2Task()` on a `StringBuilder` and verify the output does NOT list all 7 BRD section names explicitly
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct — it proves the bugs exist)
  - Document counterexamples found: CDN URL is external; prompt lacks "NEVER" + "Insufficient data" prohibition; Phase 2 task lacks explicit section names
  - Mark task complete when tests are written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.3, 2.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** — Diagram Rendering Pipeline & BRD Parsing Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - **Preservation A — BRD Parsing**: Observe `BrdResponseParser.parse()` behavior on UNFIXED code for BRDs with all 7 sections containing substantive content. Write property-based test using `Arb.brdMarkdown()` generators: for all complete BRD markdown, parser returns all 7 sections with correct headings and content unchanged
  - **Preservation B — Quality Checker**: Observe `DocumentQualityChecker.check()` behavior on UNFIXED code for complete BRDs. Verify it continues to detect "Insufficient data" markers as quality issues
  - **Preservation C — Prompt Structure**: Observe `appendBrdSections("BRD")` output on UNFIXED code. Verify it still contains all 7 BRD section headings from `BrdPromptBuilder.BRD_SECTIONS`, the sub-section hints, and the AS-IS vs TO-BE process guidance
  - Verify tests pass on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 3. Fix BRD diagram viewer CDN dependency and prompt section enforcement

  - [x] 3.1 Update `DrawioDiagramRenderer.VIEWER_CDN` to local path
    - Change `VIEWER_CDN` constant from `"https://viewer.diagrams.net/js/viewer-static.min.js"` to `"/js/viewer-static.min.js"`
    - No other changes to `loadViewerScript()`, `ensureViewerLoaded()`, `initViewer()`, or `renderWithViewer()` — they work identically with a local path
    - _Bug_Condition: isBugCondition_DiagramViewer(input) where input.scriptSrc = external CDN URL AND input.loadResult = FAILURE_
    - _Expected_Behavior: Script loads from local `/js/viewer-static.min.js`, viewerLoaded = true without internet_
    - _Preservation: GraphViewer.createViewerForElement pipeline, initViewer() setup, fallback for invalid XML all unchanged_
    - _Requirements: 2.1, 2.2, 3.1, 3.2_

  - [x] 3.2 Bundle draw.io viewer script locally
    - Download `viewer-static.min.js` from draw.io open source project (Apache 2.0 license)
    - Place at `frontend/src/jsMain/resources/js/viewer-static.min.js` so Vite serves it at `/js/viewer-static.min.js`
    - _Requirements: 2.1, 2.2_

  - [x] 3.3 Strengthen `appendBrdSections()` prompt instructions
    - In `AgenticPromptSections.kt`, after the existing "Each section MUST have real content (3+ lines minimum)" line:
    - Add explicit prohibition: `"CRITICAL: NEVER write 'Insufficient data', 'N/A', 'No data available', or similar placeholder text in ANY section."`
    - Add Project Requirements sub-section requirements: require Process Overview, Functional Requirements, Non-Functional Requirements, Data Requirements
    - Add inference instruction: `"If you lack specific data for a section, use your analysis of the ticket to infer reasonable content."`
    - _Bug_Condition: isBugCondition_InsufficientData(input) where promptText lacks explicit prohibition of placeholder text_
    - _Expected_Behavior: Prompt contains "NEVER" + "Insufficient data" prohibition AND requires Project Requirements sub-sections_
    - _Preservation: All 7 BRD section headings, sub-section hints, AS-IS vs TO-BE guidance remain unchanged_
    - _Requirements: 2.3, 2.5, 3.3, 3.4_

  - [x] 3.4 Strengthen `appendPhase2Task()` prompt instructions
    - In `PhasePromptBuilder.kt`, expand `appendPhase2Task()` to:
    - List all 7 BRD section names explicitly (Revision History, Project Overview, Common Project Acronyms, Existing Processes, Project Requirements, Sign Off, Appendix)
    - Add mandatory content reinforcement: all 7 sections must have substantive content, no placeholders allowed
    - _Bug_Condition: Phase 2 task instruction lacks explicit section names and content enforcement_
    - _Expected_Behavior: Phase 2 prompt contains all 7 section names AND mandatory content instruction_
    - _Preservation: Phase 1 and Phase 3 prompts unchanged; diagram generation unchanged_
    - _Requirements: 2.4, 3.5_

  - [x] 3.5 Verify bug condition exploration tests now pass
    - **Property 1: Expected Behavior** — CDN Dependency & Weak Prompt Enforcement
    - **IMPORTANT**: Re-run the SAME tests from task 1 — do NOT write new tests
    - The tests from task 1 encode the expected behavior
    - When these tests pass, it confirms the expected behavior is satisfied
    - Run bug condition exploration tests from step 1
    - **EXPECTED OUTCOME**: Tests PASS (confirms bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 3.6 Verify preservation tests still pass
    - **Property 2: Preservation** — Diagram Rendering Pipeline & BRD Parsing Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 4. Checkpoint — Ensure all tests pass
  - Run full test suite: `./gradlew :shared:jvmTest` and `./gradlew :server:test`
  - Verify all existing property-based tests still pass (PromptCompletenessPropertyTest, ParserSectionPropertyTest, MarkdownRoundTripPropertyTest, HeadingParsingPreservationTest)
  - Verify new bug condition and preservation tests pass
  - Ensure all tests pass, ask the user if questions arise.
