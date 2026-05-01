# Bugfix Requirements Document

## Introduction

This bugfix addresses two related BRD document quality issues that degrade the user experience when viewing generated BRD documents:

1. **Draw.io Diagram Viewer Unavailable (Frontend)** — BRD documents display raw XML with a "viewer unavailable" fallback message instead of rendered diagrams when the draw.io CDN script fails to load (offline, firewall, CORS issues).

2. **"Insufficient data" in Project Requirements Section (Backend)** — The "Project Requirements" section (and occasionally other sections) of generated BRDs shows the "⚠️ Insufficient data" marker even when ticket data is available, because the AI prompt does not strongly enforce that ALL 7 BRD sections must contain real content.

Both bugs directly impact BRD document quality — the first makes diagrams unreadable, the second produces incomplete documents that require manual rework.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the draw.io CDN (`https://viewer.diagrams.net/js/viewer-static.min.js`) fails to load (offline environment, firewall block, CORS error, or CDN downtime) THEN the system displays "📊 Draw.io diagram (viewer unavailable)" with truncated raw XML instead of a rendered diagram

1.2 WHEN the user is in an environment without internet access THEN the system cannot render any draw.io diagrams in BRD documents, making all embedded diagrams unreadable

1.3 WHEN the AI generates a BRD and produces missing or empty content for the "Project Requirements" section THEN the system fills it with "⚠️ Insufficient data — This section requires manual input. Available data from analysis: none"

1.4 WHEN the AI generates a BRD via the multi-phase pipeline (Phase 2 in `PhasePromptBuilder`) and skips or minimally populates any of the 7 BRD sections THEN the `BrdResponseParser` fills those sections with the "Insufficient data" marker

1.5 WHEN the `appendBrdSections()` prompt function is called THEN the system only instructs "Each section MUST have real content (3+ lines minimum)" without explicitly prohibiting "Insufficient data" or "N/A" responses, and without specifying required sub-content for "Project Requirements"

### Expected Behavior (Correct)

2.1 WHEN the draw.io viewer JavaScript is needed to render diagrams THEN the system SHALL load it from a locally bundled file (`/js/viewer-static.min.js`) instead of the external CDN, ensuring diagrams render without internet access

2.2 WHEN the user is in an offline environment or behind a firewall THEN the system SHALL still render all draw.io diagrams in BRD documents using the locally bundled viewer script

2.3 WHEN the AI generates a BRD THEN the system SHALL instruct the AI (via `appendBrdSections()`) that EVERY one of the 7 BRD sections MUST contain substantive real content, and that "Insufficient data", "N/A", "No data available", or similar placeholder text is NEVER acceptable as section content

2.4 WHEN the AI generates a BRD via the multi-phase pipeline (Phase 2) THEN the system SHALL include the same mandatory content enforcement instructions in the Phase 2 prompt, ensuring all 7 sections have real content

2.5 WHEN the `appendBrdSections()` prompt function instructs the AI about the "Project Requirements" section THEN the system SHALL explicitly require that it contains at minimum: Process Overview, Functional Requirements, Non-Functional Requirements, and Data Requirements sub-sections with real content derived from available ticket data

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the draw.io viewer loads successfully (whether from local bundle or any source) THEN the system SHALL CONTINUE TO render diagrams using the `GraphViewer.createViewerForElement()` API with the same configuration (highlight, nav, resize, toolbar, xml)

3.2 WHEN the draw.io viewer fails to render a specific diagram (invalid XML, render error) THEN the system SHALL show the fallback display with the "📊 Draw.io diagram (viewer unavailable)" label and full XML content (no longer truncated to 500 chars). Additionally, `sanitizeDrawioXml()` SHALL escape bare `&` characters before passing XML to GraphViewer to prevent XML parse errors.

3.3 WHEN the AI generates a BRD with all 7 sections containing substantive content THEN the `BrdResponseParser` SHALL CONTINUE TO parse and return those sections without modification

3.4 WHEN the `DocumentQualityChecker` checks a generated BRD THEN the system SHALL CONTINUE TO detect and flag any remaining "Insufficient data" markers as quality issues

3.5 WHEN the AI generates diagrams (Phase 3 or inline) THEN the diagram generation prompts and templates SHALL CONTINUE TO work unchanged

3.6 WHEN the `BrdResponseParser.parse()` encounters a genuinely missing section heading in AI output THEN the system SHALL CONTINUE TO fill it with the default "Insufficient data" text as a safety fallback

---

### Bug Condition Derivation

**Bug 1 — Draw.io CDN Failure:**

```pascal
FUNCTION isBugCondition_DiagramViewer(X)
  INPUT: X of type DiagramRenderRequest
  OUTPUT: boolean
  
  // Returns true when the CDN script fails to load
  RETURN X.cdnScriptLoadResult = FAILURE
END FUNCTION
```

```pascal
// Property: Fix Checking — Local Viewer Bundle
FOR ALL X WHERE isBugCondition_DiagramViewer(X) DO
  result ← renderDiagram'(X)
  ASSERT result.viewerLoaded = true
    AND result.diagramRendered = true
    AND no_fallback_shown(result)
END FOR
```

```pascal
// Property: Preservation Checking — Diagram Rendering
FOR ALL X WHERE NOT isBugCondition_DiagramViewer(X) DO
  ASSERT renderDiagram(X) = renderDiagram'(X)
END FOR
```

**Bug 2 — Insufficient Data in BRD Sections:**

```pascal
FUNCTION isBugCondition_InsufficientData(X)
  INPUT: X of type BrdGenerationRequest
  OUTPUT: boolean
  
  // Returns true when AI produces empty/missing content for any section
  RETURN EXISTS section IN X.aiOutput.sections
    WHERE section.content IS EMPTY
      OR section.content CONTAINS "Insufficient data"
      OR section.content CONTAINS "N/A"
END FUNCTION
```

```pascal
// Property: Fix Checking — Mandatory Section Content
FOR ALL X WHERE isBugCondition_InsufficientData(X) DO
  result ← generateBrd'(X)
  ASSERT FOR ALL section IN result.sections:
    section.content.length >= MIN_CONTENT_LENGTH
    AND NOT section.content CONTAINS "Insufficient data"
    AND NOT section.content CONTAINS "N/A"
END FOR
```

```pascal
// Property: Preservation Checking — Valid BRD Parsing
FOR ALL X WHERE NOT isBugCondition_InsufficientData(X) DO
  ASSERT generateBrd(X) = generateBrd'(X)
END FOR
```
