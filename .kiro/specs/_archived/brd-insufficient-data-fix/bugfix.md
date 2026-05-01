# Bugfix Requirements Document

## Introduction

When generating a BRD document for any ticket via the Ticket Intelligence page, all 7 BRD sections (Carleton ITS template) display the fallback text "⚠️ Insufficient data — This section requires manual input. Available data from analysis: none" instead of actual content derived from the ticket's analysis data. The BRD header is generated correctly (showing ticket ID, timestamp, source tickets), but every section body falls back to the default `INSUFFICIENT_DATA` constant.

The root cause is in the response parsing pipeline. `BrdResponseParser.parse()` delegates to `parseMarkdownSections()` in `DocumentParserUtils.kt`, which uses regex `^##\s+(.+)$` to extract headings. The parsed headings are then matched against `BrdPromptBuilder.BRD_SECTIONS` via exact string lookup (`parsed[heading]`). If the AI model returns headings with different formatting (e.g., `#` instead of `##`, numbered prefixes like `## 1. Executive Summary`, trailing whitespace, or different casing), the lookup returns `null` for every section, and all sections fall back to `INSUFFICIENT_DATA`.

A secondary contributing cause is that `DocumentAggregatorImpl.fetchMainTicket()` only validates `record.businessSummary.isNotBlank()` but does not check whether deep analysis fields (`asIsState`, `toBeState`, `extractedRequirements`, `acceptanceCriteria`, `dependencies`) contain meaningful data. If a ticket passed basic analysis but not deep analysis, the prompt CONTEXT section is nearly empty, causing the AI to follow anti-hallucination instructions and produce fallback text for all sections.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the AI model returns BRD section headings with numbering prefixes (e.g., `## 1. Executive Summary` instead of `## Executive Summary`) THEN the system fails to match any heading in `parseMarkdownSections()` because `parsed[heading]` performs exact string lookup against `BrdPromptBuilder.BRD_SECTIONS`, resulting in all sections falling back to `INSUFFICIENT_DATA`

1.2 WHEN the AI model returns BRD section headings with different casing (e.g., `## executive summary` or `## EXECUTIVE SUMMARY`) THEN the system fails to match headings because the lookup is case-sensitive, resulting in all sections falling back to `INSUFFICIENT_DATA`

1.3 WHEN the AI model returns BRD section headings using `#` (H1) instead of `##` (H2) THEN the original `HEADING_PATTERN` regex `^##\s+(.+)$` does not match H1 headings, `parseMarkdownSections()` returns `emptyMap()`, and all sections fall back to `INSUFFICIENT_DATA`. Note: H3 (`###`) sub-headings within sections (e.g., `### Functional Requirements` inside `## Project Requirements`) must NOT be treated as section boundaries — doing so splits the parent section and causes empty content.

1.4 WHEN the AI model returns headings with trailing whitespace or extra formatting (e.g., `## Executive Summary  ` or `## **Executive Summary**`) THEN the exact string match fails despite the heading being semantically correct, resulting in fallback for affected sections

1.5 WHEN a ticket has passed basic analysis (non-blank `businessSummary`) but deep analysis fields (`asIsState`, `toBeState`, `extractedRequirements`, `acceptanceCriteria`) are all empty/default THEN the prompt CONTEXT section contains minimal data, the AI follows anti-hallucination instructions, and produces the "⚠️ Insufficient data" text for all sections — which the parser then treats as valid content rather than detecting the generation quality issue

1.6 WHEN the AI call returns an empty string or a response with no markdown headings at all THEN `parseMarkdownSections()` returns `emptyMap()` and all sections fall back to `INSUFFICIENT_DATA` with no diagnostic logging to help identify the root cause

### Expected Behavior (Correct)

2.1 WHEN the AI model returns BRD section headings with numbering prefixes (e.g., `## 1. Executive Summary`) THEN the system SHALL normalize the heading by stripping numbering prefixes before matching, and successfully map the content to the corresponding BRD section

2.2 WHEN the AI model returns BRD section headings with different casing THEN the system SHALL perform case-insensitive heading matching and successfully map the content to the corresponding BRD section

2.3 WHEN the AI model returns BRD section headings using `#` (H1) or `###` (H3) instead of `##` (H2) THEN the system SHALL recognize headings at all levels. H1/H2 headings are used as section boundaries (preferred). If no H1/H2 found, H3 headings are used as fallback boundaries. When H1/H2 boundaries exist, H3 sub-headings are preserved as content within their parent section — they do not create new section boundaries.

2.4 WHEN the AI model returns headings with trailing whitespace or markdown formatting (e.g., bold markers `**`) THEN the system SHALL strip extra whitespace and formatting before matching, and successfully map the content to the corresponding BRD section

2.5 WHEN a ticket has passed basic analysis but deep analysis fields are all empty/default THEN the system SHALL include a warning in the generation context or log indicating limited data availability, so that the resulting BRD clearly reflects which specific data points were available versus missing per section (rather than a blanket "none" for all sections)

2.6 WHEN the AI call returns an empty string or a response with no recognizable headings THEN the system SHALL log a warning with the raw response length and first 200 characters for diagnostic purposes, before falling back to `INSUFFICIENT_DATA` for all sections

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the AI model returns BRD section headings exactly matching `BrdPromptBuilder.BRD_SECTIONS` with `##` prefix (e.g., `## Executive Summary`) THEN the system SHALL CONTINUE TO parse and map all sections correctly as before

3.2 WHEN the AI response contains all BRD sections with valid content THEN the system SHALL CONTINUE TO produce a complete BRD document with all sections populated

3.3 WHEN the AI response is missing some sections (e.g., only 5 out of 18 headings present) THEN the system SHALL CONTINUE TO fill missing sections with the `INSUFFICIENT_DATA` default text

3.4 WHEN `BrdResponseParser.serialize()` is called on parsed sections THEN the system SHALL CONTINUE TO produce valid Markdown with `## ` headings that can be round-tripped through parse → serialize

3.5 WHEN a ticket has not been analyzed at all (no KBRecord or blank `businessSummary`) THEN `DocumentAggregatorImpl.fetchMainTicket()` SHALL CONTINUE TO throw an error requiring analysis before document generation

3.6 WHEN linked tickets are missing from the Knowledge Base THEN `DocumentAggregatorImpl` SHALL CONTINUE TO skip them with a log warning and proceed with generation

3.7 WHEN source citations in format `[Source: TICKET-ID]` are present in section content THEN `extractSourceCitations()` SHALL CONTINUE TO extract them correctly into `DocumentSection.sourceRefs`
